using System.Net;
using System.Net.Sockets;
using System.Text;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;

internal sealed class ControllerServer : IDisposable
{
    private const int DiscoveryPort = 5556;
    private const string DiscoveryRequest = "CUSTOM_CONTROLLER_DISCOVER";
    private const string DiscoveryResponsePrefix = "CUSTOM_CONTROLLER";

    private readonly int _port;

    // Controller input socket: KEEP UDP 5555 behavior unchanged.
    private readonly Socket _socket;

    // Discovery socket: NEW UDP 5556.
    private readonly UdpClient _discoveryClient;

    private readonly byte[] _buffer =
        new byte[ControllerPacket.Size];

    private readonly EndPoint _remote =
        new IPEndPoint(IPAddress.Any, 0);

    private readonly ViGEmClient _vigem;
    private readonly IXbox360Controller _controller;

    private readonly Thread _receiveThread;
    private readonly Thread _discoveryThread;

    private volatile bool _running;

    private bool _hasSequence;
    private byte _lastSequence;

    private long _accepted;
    private long _dropped;

    public ControllerServer(int port)
    {
        _port = port;

        /*
         * ------------------------------------------------------------
         * 1. Existing controller UDP socket
         * ------------------------------------------------------------
         */
        _socket = new Socket(
            AddressFamily.InterNetwork,
            SocketType.Dgram,
            ProtocolType.Udp)
        {
            ReceiveBufferSize = 64 * 1024,
            SendBufferSize = 8 * 1024
        };

        _socket.Bind(
            new IPEndPoint(
                IPAddress.Any,
                port));

        /*
         * ------------------------------------------------------------
         * 2. ViGEm
         * ------------------------------------------------------------
         */
        try
        {
            Console.WriteLine(
                "Initializing ViGEmClient...");

            _vigem = new ViGEmClient();

            Console.WriteLine(
                "ViGEmClient initialized.");

            _controller =
                _vigem.CreateXbox360Controller();

            Console.WriteLine(
                "Xbox 360 virtual controller created.");

            _controller.AutoSubmitReport = false;

            _controller.Connect();

            Console.WriteLine(
                "Virtual Xbox controller connected.");
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(
                "=== ViGEm initialization failed ===");

            Console.Error.WriteLine(
                ex.ToString());

            /*
             * Clean up the controller UDP socket if
             * ViGEm initialization fails.
             */
            try
            {
                _socket.Close();
            }
            catch
            {
            }

            throw;
        }

        /*
         * ------------------------------------------------------------
         * 3. Discovery UDP socket
         *
         * UDP 5556 is independent from controller UDP 5555.
         * This means discovery cannot interfere with controller packets.
         * ------------------------------------------------------------
         */
        _discoveryClient = new UdpClient();

        _discoveryClient.Client.SetSocketOption(
            SocketOptionLevel.Socket,
            SocketOptionName.ReuseAddress,
            true);

        _discoveryClient.Client.Bind(
            new IPEndPoint(
                IPAddress.Any,
                DiscoveryPort));

        _discoveryClient.EnableBroadcast = true;

        /*
         * ------------------------------------------------------------
         * 4. Existing controller receive thread
         * ------------------------------------------------------------
         */
        _receiveThread = new Thread(ReceiveLoop)
        {
            IsBackground = true,
            Name = "UDP-Input-Receiver",
            Priority = ThreadPriority.Highest
        };

        /*
         * ------------------------------------------------------------
         * 5. New discovery thread
         * ------------------------------------------------------------
         */
        _discoveryThread =
            new Thread(DiscoveryLoop)
            {
                IsBackground = true,
                Name = "UDP-Discovery-Receiver",
                Priority = ThreadPriority.Normal
            };
    }

    public void Start()
    {
        _running = true;

        _receiveThread.Start();
        _discoveryThread.Start();

        Console.WriteLine(
            $"Custom Controller Server");

        Console.WriteLine(
            $"UDP listening on 0.0.0.0:{_port}");

        Console.WriteLine(
            $"Discovery listening on 0.0.0.0:{DiscoveryPort}");

        Console.WriteLine(
            "Waiting for Android controller...");

        Console.WriteLine(
            "Press Ctrl+C to stop.");
    }

    /*
     * ================================================================
     * CONTROLLER INPUT
     * ================================================================
     *
     * This is intentionally kept compatible with the previous version.
     */
    private void ReceiveLoop()
    {
        var remote = _remote;

        while (_running)
        {
            try
            {
                EndPoint endpoint = remote;

                int count = _socket.ReceiveFrom(
                    _buffer,
                    0,
                    _buffer.Length,
                    SocketFlags.None,
                    ref endpoint);

                if (count != ControllerPacket.Size)
                    continue;

                byte sequence = _buffer[0];

                if (_hasSequence &&
                    !ControllerPacket.IsNewer(
                        sequence,
                        _lastSequence))
                {
                    Interlocked.Increment(
                        ref _dropped);

                    continue;
                }

                _hasSequence = true;
                _lastSequence = sequence;

                ControllerPacket.ApplyToController(
                    _buffer,
                    _controller);

                long accepted =
                    Interlocked.Increment(
                        ref _accepted);

                /*
                 * Existing diagnostic output.
                 */
                if ((accepted & 0xFF) == 0)
                {
                    Console.WriteLine(
                        $"Packets: {accepted:n0} accepted, " +
                        $"{Volatile.Read(ref _dropped):n0} stale/invalid.");
                }
            }
            catch (SocketException)
                when (!_running)
            {
                break;
            }
            catch (ObjectDisposedException)
                when (!_running)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(
                    $"Receive error: {ex.Message}");
            }
        }
    }

    /*
     * ================================================================
     * DISCOVERY
     * ================================================================
     *
     * Android sends:
     *
     *     CUSTOM_CONTROLLER_DISCOVER
     *
     * to UDP 5556.
     *
     * Windows responds:
     *
     *     CUSTOM_CONTROLLER|1|PC_NAME|5555
     *
     * Example:
     *
     *     CUSTOM_CONTROLLER|1|ACER|5555
     *
     * Android can then use the sender's IP address together with
     * port 5555 for controller traffic.
     */
    private void DiscoveryLoop()
    {
        while (_running)
        {
            try
            {
                IPEndPoint? remoteEndpoint = null;

                byte[] data =
                    _discoveryClient.Receive(
                        ref remoteEndpoint);

                if (!_running)
                    break;

                string request =
                    Encoding.UTF8.GetString(data)
                        .Trim('\0', ' ', '\r', '\n');

                if (!string.Equals(
                        request,
                        DiscoveryRequest,
                        StringComparison.Ordinal))
                {
                    continue;
                }

                string machineName =
                    Environment.MachineName;

                /*
                 * Restrict characters that would interfere
                 * with the simple discovery protocol.
                 */
                machineName =
                    SanitizeMachineName(machineName);

                string response =
                    $"{DiscoveryResponsePrefix}|1|{machineName}|{_port}";

                byte[] responseBytes =
                    Encoding.UTF8.GetBytes(response);

                _discoveryClient.Send(
                    responseBytes,
                    responseBytes.Length,
                    remoteEndpoint);

                Console.WriteLine(
                    $"Discovery response sent to " +
                    $"{remoteEndpoint.Address}:{remoteEndpoint.Port}");
            }
            catch (SocketException)
                when (!_running)
            {
                break;
            }
            catch (ObjectDisposedException)
                when (!_running)
            {
                break;
            }
            catch (Exception ex)
            {
                if (_running)
                {
                    Console.Error.WriteLine(
                        $"Discovery error: {ex.Message}");
                }
            }
        }
    }

    private static string SanitizeMachineName(
        string machineName)
    {
        if (string.IsNullOrWhiteSpace(machineName))
            return "Windows PC";

        return machineName
            .Replace("|", "_")
            .Replace("\r", "_")
            .Replace("\n", "_")
            .Trim();
    }

    /*
     * ================================================================
     * DISPOSE
     * ================================================================
     */
    public void Dispose()
    {
        _running = false;

        /*
         * Closing both sockets wakes their receive loops.
         */
        try
        {
            _socket.Close();
        }
        catch
        {
        }

        try
        {
            _discoveryClient.Close();
        }
        catch
        {
        }

        try
        {
            if (_receiveThread.IsAlive)
            {
                _receiveThread.Join(1000);
            }
        }
        catch
        {
        }

        try
        {
            if (_discoveryThread.IsAlive)
            {
                _discoveryThread.Join(1000);
            }
        }
        catch
        {
        }

        /*
         * Release virtual controller safely.
         */
        try
        {
            _controller.SetButtonsFull(0);

            _controller.LeftTrigger = 0;
            _controller.RightTrigger = 0;

            _controller.LeftThumbX = 0;
            _controller.LeftThumbY = 0;

            _controller.RightThumbX = 0;
            _controller.RightThumbY = 0;

            _controller.SubmitReport();
        }
        catch
        {
        }

        try
        {
            _controller.Disconnect();
        }
        catch
        {
        }

        try
        {
            _vigem.Dispose();
        }
        catch
        {
        }

        try
        {
            _socket.Dispose();
        }
        catch
        {
        }

        try
        {
            _discoveryClient.Dispose();
        }
        catch
        {
        }
    }
}