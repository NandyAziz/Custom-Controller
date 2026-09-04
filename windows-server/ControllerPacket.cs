using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;
using System.Buffers.Binary;

internal static class ControllerPacket
{
    public const int Size = 13;

    public const byte DigitalCross = 1 << 0;
    public const byte DigitalCircle = 1 << 1;
    public const byte DigitalSquare = 1 << 2;
    public const byte DigitalTriangle = 1 << 3;
    public const byte DigitalL1 = 1 << 4;
    public const byte DigitalR1 = 1 << 5;
    public const byte DigitalSelect = 1 << 6;
    public const byte DigitalStart = 1 << 7;

    public const byte ExtraPs = 1 << 0;
    public const byte ExtraDpadUp = 1 << 1;
    public const byte ExtraDpadDown = 1 << 2;
    public const byte ExtraDpadLeft = 1 << 3;
    public const byte ExtraDpadRight = 1 << 4;

    public static bool IsNewer(byte candidate, byte last)
    {
        int delta = (candidate - last) & 0xFF;
        return delta is >= 1 and <= 127;
    }

    public static short ReadInt16LittleEndian(byte[] buffer, int offset) =>
        BinaryPrimitives.ReadInt16LittleEndian(buffer.AsSpan(offset, 2));

    public static void ApplyToController(byte[] packet, IXbox360Controller controller)
    {
        byte d = packet[1];
        byte e = packet[2];

        controller.SetButtonState(Xbox360Button.A, (d & DigitalCross) != 0);
        controller.SetButtonState(Xbox360Button.B, (d & DigitalCircle) != 0);
        controller.SetButtonState(Xbox360Button.X, (d & DigitalSquare) != 0);
        controller.SetButtonState(Xbox360Button.Y, (d & DigitalTriangle) != 0);
        controller.SetButtonState(Xbox360Button.LeftShoulder, (d & DigitalL1) != 0);
        controller.SetButtonState(Xbox360Button.RightShoulder, (d & DigitalR1) != 0);
        controller.SetButtonState(Xbox360Button.Back, (d & DigitalSelect) != 0);
        controller.SetButtonState(Xbox360Button.Start, (d & DigitalStart) != 0);
        controller.SetButtonState(Xbox360Button.Guide, (e & ExtraPs) != 0);
        controller.SetButtonState(Xbox360Button.Up, (e & ExtraDpadUp) != 0);
        controller.SetButtonState(Xbox360Button.Down, (e & ExtraDpadDown) != 0);
        controller.SetButtonState(Xbox360Button.Left, (e & ExtraDpadLeft) != 0);
        controller.SetButtonState(Xbox360Button.Right, (e & ExtraDpadRight) != 0);

        controller.LeftThumbX = ReadInt16LittleEndian(packet, 3);
        controller.LeftThumbY = ReadInt16LittleEndian(packet, 5);
        controller.RightThumbX = ReadInt16LittleEndian(packet, 7);
        controller.RightThumbY = ReadInt16LittleEndian(packet, 9);
        controller.LeftTrigger = packet[11];
        controller.RightTrigger = packet[12];
        controller.SubmitReport();
    }
}