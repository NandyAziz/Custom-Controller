using System.Net;
using System.Net.Sockets;
using System.Threading;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

const int Port = 5555;
const int PacketSize = ControllerPacket.Size;

using var server = new ControllerServer(Port);
server.Start();

Console.WriteLine("Custom Controller Server");
Console.WriteLine($"UDP listening on 0.0.0.0:{Port}");
Console.WriteLine("Waiting for Android controller...");
Console.WriteLine("Press Ctrl+C to stop.");

using var quit = new ManualResetEventSlim(false);
Console.CancelKeyPress += (_, e) =>
{
    e.Cancel = true;
    quit.Set();
};

quit.Wait();
