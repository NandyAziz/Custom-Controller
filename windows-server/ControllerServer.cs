using System.Net;
using System.Net.Sockets;
using System.Diagnostics;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;

internal sealed class ControllerServer : IDisposable
{
    private readonly int _port;
    private readonly Socket _socket;
    private readonly byte[] _buffer = new byte[ControllerPacket.Size];
    private readonly EndPoint _remote = new IPEndPoint(IPAddress.Any, 0);
    private readonly ViGEmClient _vigem;
    private readonly IXbox360Controller _controller;
    private readonly Thread _receiveThread;
    private volatile bool _running;
    private bool _hasSequence;
    private byte _lastSequence;
    private long _accepted;
    private long _dropped;

    public ControllerServer(int port)
    {
        _port = port;
        _socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp)
        {
            ReceiveBufferSize = 64 * 1024,
            SendBufferSize = 8 * 1024
        };
        _socket.Bind(new IPEndPoint(IPAddress.Any, port));

        _vigem = new ViGEmClient();
        _controller = _vigem.CreateXbox360Controller();
        _controller.AutoSubmitReport = false;
        _controller.Connect();

        _receiveThread = new Thread(ReceiveLoop)
        {
            IsBackground = true,
            Name = "UDP-Input-Receiver",
            Priority = ThreadPriority.Highest
        };
    }

    public void Start()
    {
        _running = true;
        _receiveThread.Start();
    }

    private void ReceiveLoop()
    {
        var remote = _remote;

        while (_running)
        {
            try
            {
                EndPoint endpoint = remote;
                int count = _socket.ReceiveFrom(_buffer, 0, _buffer.Length, SocketFlags.None, ref endpoint);

                if (count != ControllerPacket.Size)
                    continue;

                byte sequence = _buffer[0];

                if (_hasSequence && !ControllerPacket.IsNewer(sequence, _lastSequence))
                {
                    Interlocked.Increment(ref _dropped);
                    continue;
                }

                _hasSequence = true;
                _lastSequence = sequence;

                ControllerPacket.ApplyToController(_buffer, _controller);
                long accepted = Interlocked.Increment(ref _accepted);

                if ((accepted & 0xFF) == 0)
                    Console.WriteLine($"Packets: {accepted:n0} accepted, {Volatile.Read(ref _dropped):n0} stale/invalid.");
            }
            catch (SocketException) when (!_running)
            {
                break;
            }
            catch (ObjectDisposedException) when (!_running)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"Receive error: {ex.Message}");
            }
        }
    }

    public void Dispose()
    {
        _running = false;

        try { _socket.Close(); } catch { }
        try { if (_receiveThread.IsAlive) _receiveThread.Join(1000); } catch { }

        try { _controller.SetButtonsFull(0); _controller.LeftTrigger = 0; _controller.RightTrigger = 0;
              _controller.LeftThumbX = 0; _controller.LeftThumbY = 0; _controller.RightThumbX = 0; _controller.RightThumbY = 0;
              _controller.SubmitReport(); } catch { }

        try { _controller.Disconnect(); } catch { }
       
        _vigem.Dispose();
        _socket.Dispose();
    }
}
