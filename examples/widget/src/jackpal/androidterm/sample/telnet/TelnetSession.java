package jackpal.androidterm.sample.telnet;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import android.util.Log;

import jackpal.androidterm.emulatorview.TermSession;

/**
 * A rudimentary Telnet client implemented as a subclass of TermSession.
 *
 * Telnet, as specified in RFC 854, is a fairly simple protocol: for the
 * most part, we send and receive streams of bytes which can be fed directly
 * into the terminal emulator.  However, there are a handful of complications:
 *
 * - The standard says that CR (ASCII carriage return) must either be
 *   translated into the network standard newline sequence CR LF, or be
 *   followed immediately by NUL.
 * - (byte) 255, called IAC in the standard, introduces Telnet command
 *   sequences which can be used to negotiate options, perform certain
 *   actions on the "Network Virtual Terminal" which the standard defines,
 *   or do flow control.
 * - By default, the protocol spoken is designed to accommodate a half-duplex
 *   terminal on either end, so we should be able to buffer output and
 *   send it on a trigger (the sequence IAC GA).
 * - By default, we're expected to be able to echo local keyboard input into
 *   our own output.
 *
 * To solve these problems, we filter the input from the network to catch
 * and implement Telnet commands via the processInput() method.  Similarly, we
 * filter the output from TermSession by overriding write() to modify CR as
 * required by the standard, and pass a buffer with manually controllable
 * flushing to the TermSession to use as its output stream.
 *
 * In addition to the base Telnet protocol, we implement two options:
 * the ECHO option (RFC 857) for enabling echoing of input across the network,
 * and the SUPPRESS-GO-AHEAD option (RFC 858) for omitting half-duplex flow
 * control.  Both of these are commonly available from servers, and make our
 * lives easier.
 */
public class TelnetSession extends TermSession
{
    private static final String TAG = "TelnetSession";
    private static final boolean DEBUG = false;

    public static final int IAC = 255;

    public static final int CMD_SE = 240;	// SE -- end of parameters
    public static final int CMD_NOP = 241;	// NOP
    public static final int CMD_MARK = 242;	// data mark
    public static final int CMD_BRK = 243;	// send BREAK to terminal
    public static final int CMD_IP = 244;	// Interrupt Process
    public static final int CMD_AO = 245;	// Abort Output
    public static final int CMD_AYT = 246;	// Are You There
    public static final int CMD_EC = 247;	// Erase Character
    public static final int CMD_EL = 248;	// Erase Line
    public static final int CMD_GA = 249;	// Go Ahead (clear to send)
    public static final int CMD_SB = 250;	// SB -- begin parameters
    public static final int CMD_WILL = 251;	// used in option negotiation
    public static final int CMD_WONT = 252;	// used in option negotiation
    public static final int CMD_DO = 253;	// used in option negotiation
    public static final int CMD_DONT = 254;	// used in option negotiation

    public static final int OPTION_ECHO = 1; // see RFC 857
    public static final int OPTION_SUPPRESS_GO_AHEAD = 3; // see RFC 858
    public static final int OPTION_RANDOMLY_LOSE = 256; // see RFC 748 :)

    // Whether we believe the remote end implements the telnet protocol
    private boolean peerIsTelnetd = false;

    private boolean peerEchoesInput = false;
    /* RFC 854 says false is the default, but that makes the client effectively
       useless for connecting to random non-Telnet servers for debugging */
    private boolean peerSuppressedGoAhead = true;

    private boolean echoInput = false;
    /* RFC 854 says false is the default, but that makes the client effectively
       useless for connecting to random non-Telnet servers for debugging */
    private boolean suppressGoAhead = true;
    private boolean doSuppressGaRequested = false;
    private boolean willSuppressGaRequested = false;

    /* Telnet command processor state */
    private boolean mInTelnetCommand = false;
    private int mTelnetCommand = 0;
    private int mTelnetCommandArg = 0;
    private boolean mMultipleParameters = false;
    private int mLastInputByteProcessed = 0;

    /**
     * Create a TelnetSession to handle the telnet protocol and terminal
     * emulation, using an existing InputStream and OutputStream.
     */
    public TelnetSession(InputStream termIn, OutputStream termOut) {
        setTermIn(termIn);
        setTermOut(termOut);
    }

    /**
     * Process data before sending it to the server.
     * We replace all occurrences of \r with \r\n, as required by the
     * Telnet protocol (CR meant to be a newline should be sent as CR LF,
     * and all other CRs must be sent as CR NUL).
     */
    @Override
    public void write(byte[] bytes, int offset, int count) {
        // Count the number of CRs
        int numCRs = 0;
        for (int i = offset; i < offset + count; ++i) {
            if (bytes[i] == '\r') {
                ++numCRs;
            }
        }

        if (numCRs == 0) {
            // No CRs -- just send data as-is
            doWrite(bytes, offset, count);

            if (isRunning() && !peerEchoesInput) {
                doLocalEcho(bytes);
            }
            return;
        }

        // Convert CRs into CRLFs
        byte[] translated = new byte[count + numCRs];
        int j = 0;
        for (int i = offset; i < offset + count; ++i) {
            if (bytes[i] == '\r') {
                translated[j++] = '\r';
                translated[j++] = '\n';
            } else {
                translated[j++] = bytes[i];
            }
        }

        // Send the data
        doWrite(translated, 0, translated.length);

        // If server echo is off, echo the entered characters locally
        if (isRunning() && !peerEchoesInput) {
            doLocalEcho(translated);
        }
    }

    private byte[] mWriteBuf = new byte[4096];
    private int mWriteBufLen = 0;

    /* Send data to the server, buffering it first if necessary */
    private void doWrite(byte[] data, int offset, int count) {
        if (peerSuppressedGoAhead) {
           // No need to buffer -- send it straight to the server
           super.write(data, offset, count);
           return;
        }

        /* Flush the buffer if it's full ... not strictly correct, but better
           than the alternatives */
        byte[] buffer = mWriteBuf;
        int bufLen = mWriteBufLen;
        if (bufLen + count > buffer.length) {
            flushWriteBuf();
            bufLen = 0;
        }

        // Queue the data to be sent at the next server GA
        System.arraycopy(data, offset, buffer, bufLen, count);
        mWriteBufLen += count;
    }

    /* Flush the buffer of data to be written to the server */
    private void flushWriteBuf() {
        super.write(mWriteBuf, 0, mWriteBufLen);
        mWriteBufLen = 0;
    }

    /* Echoes local input from the emulator back to the emulator screen. */
    private void doLocalEcho(byte[] data) {
        if (DEBUG) {
            Log.d(TAG, "echoing " +
                    Arrays.toString(data) + " back to terminal");
        }
        appendToEmulator(data, 0, data.length);
        notifyUpdate();
    }

    /**
     * Input filter which handles Telnet commands and copies data to the
     * terminal emulator.
     */
    @Override
    public void processInput(byte[] buffer, int offset, int count) {
        int lastByte = mLastInputByteProcessed;
        for (int i = offset; i < offset + count; ++i) {
            // need to interpret the byte as unsigned -- thanks Java!
            int curByte = ((int) buffer[i]) & 0xff;

            if (DEBUG) {
                Log.d(TAG, "input byte " + curByte);
            }

            if (mInTelnetCommand) {
                // Previous byte was part of a command sequence
                doTelnetCommand(curByte);
                lastByte = curByte;
                continue;
            }

            switch (curByte) {
            case IAC: // Telnet command prefix
                mInTelnetCommand = true;
                /* Assume we're talking to a real Telnet server */
                if (!peerIsTelnetd) {
                    doTelnetInit();
                }
                break;
            case CMD_GA: // GA -- clear to send
                /**
                 * If we're in half-duplex flow control, we've been given
                 * permission to send data; flush our output buffers.
                 *
                 * Note that it's not strictly correct to send the other
                 * side a GA at this point, but since we're not actually
                 * attached to a half-duplex terminal, we don't have a signal
                 * to indicate when the other side should logically begin
                 * to send again.
                 *
                 * In full-duplex operation (option SUPPRESS-GO-AHEAD enabled),
                 * does nothing.
                 */
                byte[] cmdGa = { (byte) IAC, (byte) CMD_GA };
                if (!peerSuppressedGoAhead) {
                    if (!suppressGoAhead) {
                        doWrite(cmdGa, 0, cmdGa.length);
                    }
                    flushWriteBuf();
                }
                break;
            case 0: // NUL -- should be ignored following a CR
                if (lastByte == '\r') {
                    if (echoInput) {
                        // We do need to echo it back to the server, though
                        doEchoInput(0);
                    }
                    break;
                }
            default:
                /* Send the data to the terminal emulator, and echo it back
                   across the network if the other end wants us to do so. */
                super.processInput(buffer, i, 1);
                if (echoInput) {
                    doEchoInput(buffer[i]);
                }
            }
            lastByte = curByte;
        }

        // Save the last byte processed -- we may need it
        mLastInputByteProcessed = lastByte;
    }

    byte[] mOneByte = new byte[1];
    private void doEchoInput(int input) {
        if (DEBUG) {
            Log.d(TAG, "echoing " + input + " to remote end");
        }
        byte[] oneByte = mOneByte;
        oneByte[0] = (byte) input;
        super.write(oneByte, 0, 1);
    }

    /**
     * Interpreter for Telnet commands.
     */
    private void doTelnetCommand(int curByte) {
        /* Handle parameter lists */
        if (mMultipleParameters) {
            switch (curByte) {
            case CMD_SE: // SE -- end of parameters
                doMultiParamCommand();
                finishTelnetCommand();
                return;
            default:
                addMultiParam(curByte);
                return;
            }
        }

        /* Handle option negotiation */
        switch (mTelnetCommand) {
        case CMD_WILL:
            handleWillOption(curByte);
            return;
        case CMD_WONT:
            handleWontOption(curByte);
            return;
        case CMD_DO:
            handleDoOption(curByte);
            return;
        case CMD_DONT:
            handleDontOption(curByte);
            return;
        }

        /* Telnet commands */
        switch (curByte) {
        case CMD_EC: // EC -- erase character
            // ESC [ D (VT100 cursor left)
            byte[] cmdLeft = { (byte) 27, (byte) '[', (byte) 'D' };
            // ESC [ P (VT100 erase char at cursor)
            byte[] cmdErase = { (byte) 27, (byte) '[', (byte) 'P' };
            super.processInput(cmdLeft, 0, cmdLeft.length);
            super.processInput(cmdErase, 0, cmdErase.length);
            break;
        case CMD_EL: // EL -- erase line
            // ESC [ 2 K (VT100 clear whole line)
            byte[] cmdEl = { (byte) 27, (byte) '[', (byte) '2', (byte) 'K' };
            super.processInput(cmdEl, 0, cmdEl.length);
            break;
        case IAC: // send the IAC character to terminal
            byte[] iac = { (byte) IAC };
            super.processInput(iac, 0, iac.length);
            break;
        case CMD_SB: // SB -- more parameters follow option
            mMultipleParameters = true;
            return;
        case CMD_WILL: // WILL
        case CMD_WONT: // WON'T
        case CMD_DO: // DO
        case CMD_DONT: // DON'T
            // Option negotiation -- save the command and wait for the option
            mTelnetCommand = curByte;
            return;
        case CMD_AYT: // AYT -- Are You There
            /**
             * RFC 854 says we should send back "some visible (i.e., printable)
             * evidence that the AYT was received" ... this is as good as
             * anything else
             */
            byte[] msg = "yes, I'm here\r\n".getBytes();
            super.write(msg, 0, msg.length);
            break;
        // The following are unimplemented
        case CMD_MARK:	// data mark
        case CMD_BRK:	// send a break to the terminal
        case CMD_IP:	// IP -- interrupt process
        case CMD_AO:	// AO -- abort output
        case CMD_NOP:	// NOP
        default:
            break;
        }

        finishTelnetCommand();
    }

    // end of command, process next byte normally
    private void finishTelnetCommand() {
        mTelnetCommand = 0;
        mInTelnetCommand = false;
        mMultipleParameters = false;
    }

    private void addMultiParam(int curByte) {
        // unimplemented
    }

    private void doMultiParamCommand() {
        // unimplemented
    }

    /**
     * Telnet option negotiation code.
     *
     * Because the Telnet protocol is defined to be mostly symmetric with
     * respect to the client and server roles, option negotiation can be
     * somewhat confusing.  The same commands are used to initiate and
     * respond to negotiation requests, and their exact meaning depends on
     * whether they were sent as an initial request or as a response:
     *
     * - WILL:  If sent as a request, indicates that we wish to enable the
     *          option on our end.  If sent as a response, indicates that we
     *          have enabled the specified option on our end.
     * - WON'T: If sent as a request, indicates that we insist on disabling the
     *          option on our end.  If sent as a response, indicates that we
     *          refuse to enable the specified option on our end.
     * - DO:    If sent as a request, indicates that we wish the peer to enable
     *          this option on the remote end.  If sent as a response, indicates
     *          that we accept the peer's request to enable the option on the
     *          remote end.
     * - DON'T: If sent as a request, indicates that we demand the peer disable
     *          this option on the remote end.  If sent as a response, indicates
     *          that we refuse to allow the peer to enable this option on the
     *          remote end.
     *
     * All options are off by default (options have to be explicitly requested).
     * In order to prevent negotiation loops, we are not supposed to reply to
     * requests which do not change the state of an option (e.g. if the server
     * sends DON'T ECHO and we're not echoing back what the server sends us, we
     * should not reply with WON'T ECHO).
     *
     * Examples:
     *
     * - server sends WILL ECHO, we reply DO ECHO: the server asks, and we
     *   agree, that the server echo the input we send to it back to us over
     *   the network.
     * - we send WON'T ECHO, server replies DON'T ECHO: we ask, and the server
     *   agrees, that we not echo the input we receive from the server back to
     *   the server over the network.
     * - we send DO SUPPRESS-GO-AHEAD, server replies WILL SUPPRESS-GO-AHEAD:
     *   we ask, and the server agrees, that the server not send GA to indicate
     *   when it's ready to take data (in other words, we can freely send data
     *   to the server).
     * - server sends DO ECHO, we reply WON'T ECHO: the server asks us to
     *   echo the input we receive from it back over the network, but we refuse
     *   to do so.
     */
    private void handleWillOption(int curByte) {
        switch (curByte) {
        case OPTION_ECHO: // WILL ECHO
            // We don't ever request DO ECHO, so this must be a request
            if (!peerEchoesInput) {
                sendOption(CMD_DO, OPTION_ECHO);
            }
            peerEchoesInput = true;
            break;
        case OPTION_SUPPRESS_GO_AHEAD: // WILL SUPPRESS-GO-AHEAD
            if (!doSuppressGaRequested && !peerSuppressedGoAhead) {
                // This is a request which changes our state, send a reply
                sendOption(CMD_DO, OPTION_SUPPRESS_GO_AHEAD);
            }
            peerSuppressedGoAhead = true;
            doSuppressGaRequested = false;
            // Flush unwritten data in the output buffer
            flushWriteBuf();
            break;
        default: // unrecognized option
            // refuse to let other end enable unknown options
            sendOption(CMD_DONT, curByte);
            break;
        }

        finishTelnetCommand();
    }

    private void handleWontOption(int curByte) {
        switch (curByte) {
        case OPTION_ECHO: // WON'T ECHO
            // We don't ever request DO ECHO, so this must be a request
            if (peerEchoesInput) {
                sendOption(CMD_DONT, OPTION_ECHO);
            }
            peerEchoesInput = false;
            break;
        case OPTION_SUPPRESS_GO_AHEAD: // WON'T SUPPRESS-GO-AHEAD
            if (!doSuppressGaRequested && peerSuppressedGoAhead) {
                // This is a request which changes our state, send a reply
                sendOption(CMD_DONT, OPTION_SUPPRESS_GO_AHEAD);
            }
            peerSuppressedGoAhead = false;
            doSuppressGaRequested = false;
            break;
        default: // unrecognized option
            // WON'T is the default for any option, so we shouldn't reply
            break;
        }

        finishTelnetCommand();
    }

    private void handleDoOption(int curByte) {
        switch (curByte) {
        case OPTION_ECHO: // DO ECHO
            /* Other Telnet clients like netkit-telnet refuse this request when
               they receive it, since it doesn't make much sense */
            sendOption(CMD_WONT, OPTION_ECHO);
            /**
            // We don't ever request WILL ECHO, so this must be a request
            if (!echoInput) {
                sendOption(CMD_WILL, OPTION_ECHO);
            }
            echoInput = true;
             */
            break;
        case OPTION_SUPPRESS_GO_AHEAD: // DO SUPPRESS-GO-AHEAD
            if (!willSuppressGaRequested && !suppressGoAhead) {
                // This is a request which changes our state, send a reply
                sendOption(CMD_WILL, OPTION_SUPPRESS_GO_AHEAD);
            }
            suppressGoAhead = true;
            willSuppressGaRequested = false;
            break;
        default: // unrecognized option
            // refuse to enable unknown options
            sendOption(CMD_WONT, curByte);
            break;
        }

        finishTelnetCommand();
    }

    private void handleDontOption(int curByte) {
        switch (curByte) {
        case OPTION_ECHO: // DON'T ECHO
            // We don't ever request DON'T ECHO, so this must be a request
            if (echoInput) {
                sendOption(CMD_WONT, OPTION_ECHO);
            }
            echoInput = false;
            break;
        case OPTION_SUPPRESS_GO_AHEAD: // DON'T SUPPRESS-GO-AHEAD
            if (!willSuppressGaRequested && suppressGoAhead) {
                // This is a request which changes our state, send a reply
                sendOption(CMD_WONT, curByte);
            }
            suppressGoAhead = false;
            willSuppressGaRequested = false;
            break;
        default: // unrecognized option
            // DON'T is the default for any option, so we shouldn't reply
            break;
        }

        finishTelnetCommand();
    }

    /* Send an option negotiation command */
    private void sendOption(int command, int opt) {
        if (DEBUG) {
            Log.d(TAG, "sending command: " + command + " " + opt);
        }
        // option negotiation needs to bypass the write buffer
        byte[] buffer = { (byte) IAC, (byte) command, (byte) opt };
        super.write(buffer, 0, buffer.length);
    }

    private void requestDoSuppressGoAhead() {
        doSuppressGaRequested = true;
        // send IAC DO SUPPRESS-GO-AHEAD
        sendOption(CMD_DO, OPTION_SUPPRESS_GO_AHEAD);
    }

    private void requestWillSuppressGoAhead() {
        willSuppressGaRequested = true;
        // send IAC WILL SUPPRESS-GO-AHEAD
        sendOption(CMD_WILL, OPTION_SUPPRESS_GO_AHEAD);
    }

    /**
     * Called the first time processInput() encounters IAC in the input stream,
     * which is a reasonably good heuristic to determine that the other end is
     * a true Telnet server and not some SMTP/POP/IMAP/whatever server.
     *
     * When called, disables the SUPPRESS-GO-AHEAD option for both directions
     * (required by the standard, but very inconvenient when talking to
     * non-Telnet servers) and sends requests to reenable it in both directions
     * (because it's much easier for us when it's on).
     */
    private void doTelnetInit() {
        peerSuppressedGoAhead = false;
        suppressGoAhead = false;

        requestDoSuppressGoAhead();
        requestWillSuppressGoAhead();

        peerIsTelnetd = true;
    }
}
