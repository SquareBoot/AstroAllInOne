package squareboot.astro.allinone.io;

import jssc.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Manager for serial ports with listeners.
 * Provides a simple way to connect your board, to send and to receive data and to get a list containing all the available ports.
 * For each error, this class will use the {@link ConnectionError} class to give you a better explanation of the error
 * (see {@link ConnectionError#getType()}, {@link ConnectionError#getCause()} and {@link ConnectionError#getMessage()}).
 *
 * @author SquareBoot
 * @version 0.1
 * @see <a href="https://github.com/scream3r/java-simple-serial-connector">jSSC on GitHub</a>
 * @see <a href="https://code.google.com/archive/p/java-simple-serial-connector/wikis/jSSC_examples.wiki">jSSC examples - Google Code Archive</a>
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Arduino implements SerialPortEventListener {

    /**
     * List of all the listeners.
     */
    protected ArrayList<SerialMessageListener> listeners = new ArrayList<>();
    /**
     * An instance of the {@link SerialPort} class.
     */
    protected SerialPort serialPort;
    /**
     * {@code true} to lock the listeners. Used by {@link #waitFor(Arduino.ConditionChecker, long)}.
     */
    private boolean listenersDetach = false;
    /**
     * Used by {@link #waitFor(Arduino.ConditionChecker, long)} to check if the condition has been verified.
     */
    private ConditionChecker checker;
    /**
     * Temporary message received from the board.
     */
    private String tmpMsg = "";

    /**
     * Class constructor.
     */
    public Arduino() {

    }

    /**
     * Class constructor. Initializes the serial port and starts a connection.
     *
     * @param port the port of your board.
     */
    public Arduino(String port) {
        connect(port);
    }

    /**
     * Class constructor. Initializes the serial port and starts a connection.
     *
     * @param port the port of your board.
     * @param rate the baud rate.
     */
    public Arduino(String port, int rate) {
        connect(port, rate);
    }

    /**
     * Serial ports discovery.
     *
     * @return an {@code ArrayList<CommPortIdentifier>} containing all the available and not busy ports.
     */
    public static ArrayList<String> listAvailablePorts() {
        return new ArrayList<>(Arrays.asList(SerialPortList.getPortNames()));
    }

    /**
     * Returns the actual state of the board: connected or disconnected.
     *
     * @return {@code true} if the board is connected, {@code false} if otherwise.
     */
    public boolean isConnected() {
        return (serialPort != null) && serialPort.isOpened();
    }

    /**
     * Adds a listener to your class.
     *
     * @param serialMessageListener a listener.
     */
    public void addListener(SerialMessageListener serialMessageListener) {
        listeners.add(serialMessageListener);
    }

    /**
     * Connects an board to this object.
     *
     * @param port the port.
     * @param rate the baud rate.
     */
    public void connect(String port, int rate) {
        serialPort = new SerialPort(port);
        try {
            serialPort.openPort();
            serialPort.setParams(rate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.setEventsMask(SerialPort.MASK_RXCHAR);
            serialPort.addEventListener(this);

        } catch (SerialPortException e) {
            ConnectionError.Type type;
            switch (e.getExceptionType()) {
                case SerialPortException.TYPE_PORT_BUSY: {
                    type = ConnectionError.Type.PORT_BUSY;
                    break;
                }

                case SerialPortException.TYPE_PORT_ALREADY_OPENED: {
                    type = ConnectionError.Type.PORT_BUSY;
                    break;
                }


                case SerialPortException.TYPE_PORT_NOT_FOUND: {
                    type = ConnectionError.Type.PORT_NOT_FOUND;
                    break;
                }

                default: {
                    type = ConnectionError.Type.UNKNOWN;
                }
            }
            throw new ConnectionError("An error occurred during connection!", e, type);
        }
    }

    /**
     * Connects a board to this object (at the default rate of 115200).
     *
     * @param port the port.
     */
    public void connect(String port) {
        connect(port, SerialPort.BAUDRATE_115200);
    }

    /**
     * Disconnects from the Serial Port and clears the listeners list.
     *
     * @see #connect
     */
    public void disconnect() {
        try {
            if (!serialPort.closePort()) {
                throw new ConnectionError("Something went wrong during the disconnection!", ConnectionError.Type.UNABLE_TO_DISCONNECT);
            }
            listeners.clear();

        } catch (SerialPortException e) {
            throw new ConnectionError("Something went wrong during the disconnection!", e, ConnectionError.Type.UNABLE_TO_DISCONNECT);
        }
    }

    /**
     * Prints a {@code String} to the connected board.
     *
     * @param message the message you want to send.
     */
    public void println(String message) {
        new Thread(() -> {
            try {
                if (!serialPort.writeBytes(message.getBytes())) {
                    notifyError(new ConnectionError("An error occurred while sending the message!",
                            ConnectionError.Type.OUTPUT));
                }

            } catch (SerialPortException e) {
                ConnectionError.Type type;
                switch (e.getExceptionType()) {
                    case SerialPortException.TYPE_PORT_BUSY: {
                        type = ConnectionError.Type.BUSY;
                        break;
                    }

                    case SerialPortException.TYPE_PORT_NOT_OPENED: {
                        type = ConnectionError.Type.IO;
                        break;
                    }

                    default: {
                        type = ConnectionError.Type.UNKNOWN;
                    }
                }
                notifyError(new ConnectionError("An error occurred during data transfer!", e, type));
            }
        }, "Serial data sender").start();
    }

    /**
     * Sends an error event to the listeners.
     *
     * @param e the exception to notify.
     */
    protected void notifyError(Exception e) {
        for (SerialMessageListener l : listeners) {
            l.onConnectionError(e);
        }
    }

    /**
     * Prints an {@code int} to the board.
     *
     * @param number the message you want to send.
     */
    public void println(int number) {
        println(String.valueOf(number));
    }

    /**
     * Prints a character to the board.
     *
     * @param c the char you want to send.
     */
    public void println(char c) {
        println(String.valueOf(c));
    }

    /**
     * Prints a {@code double} to the board.
     *
     * @param d the number you want to send.
     */
    public void println(double d) {
        println(String.valueOf(d));
    }

    /**
     * Returns the name of the serial port currently being used..
     *
     * @return the serial port's name.
     */
    public String getSerialPortName() {
        return serialPort.getPortName();
    }

    /**
     * Stops the listeners and the current thread until the last received message becomes the one required.
     *
     * @param checker the {@link ConditionChecker} object that manages the interrupt.
     * @param timeout how many milliseconds to wait at most.
     * @return the required message.
     * @see ConditionChecker
     * @see ConditionChecker#check(String)
     */
    public String waitFor(ConditionChecker checker, long timeout) {
        if (checker == null) {
            throw new IllegalArgumentException("Null condition checker!");
        }
        this.checker = checker;
        listenersDetach = true;
        long start = System.currentTimeMillis();
        while (listenersDetach) {
            // Stop thread and check the timeout
            if ((System.currentTimeMillis() - start) >= timeout) {
                listenersDetach = false;
                throw new ConnectionError(ConnectionError.Type.NO_RESPONSE);
            }
        }
        return this.checker.getWaitedMessage();
    }

    /**
     * @return a String representation of this object.
     */
    @Override
    public String toString() {
        return "Arduino[" + (isConnected() ? (serialPort.getPortName()) : "false") + "]";
    }

    /**
     * Serial event. Receives data from the connected board.
     *
     * @param portEvent the port event.
     */
    @Override
    public void serialEvent(SerialPortEvent portEvent) {
        try {
            tmpMsg = tmpMsg + serialPort.readString();

            if (tmpMsg.contains("\n") || tmpMsg.contains("\r")) {
                String[] split = tmpMsg.split("[\\n\\r]");
                ArrayList<String> messages = new ArrayList<>();
                for (String s : split) {
                    s = s.trim();
                    if (!s.equals("")) {
                        messages.add(s);
                    }
                }

                int size = messages.size();
                if (size > 0) {
                    int i = 0;
                    while (i < (size - 1)) {
                        notifyListener(messages.get(i));
                        i++;
                    }

                    if (tmpMsg.endsWith("\n") || tmpMsg.endsWith("\r")) {
                        notifyListener(messages.get(size - 1));
                        tmpMsg = "";

                    } else {
                        tmpMsg = messages.get(size - 1);
                    }
                }
            }

        } catch (SerialPortException e) {
            notifyError(new ConnectionError("An error occurred while receiving data from the serial port!",
                    e, ConnectionError.Type.INPUT));
        }
    }

    /**
     * Sends a message to all the stored serial message listeners.
     *
     * @param msg the message.
     */
    protected void notifyListener(String msg) {
        if (msg != null) {
            msg = msg.replace("\n", "").replace("\r", "");
            if (!msg.equals("")) {
                if (listenersDetach) {
                    checker.check0(msg);

                } else {
                    for (SerialMessageListener l : listeners) {
                        l.onMessage(msg);
                    }
                }
            }
        }
    }

    /**
     * Interface used by {@link #waitFor(ConditionChecker, long)} to check if the received message is the one required.
     * Indeed, {@link #waitFor(ConditionChecker, long)} stops the thread until the last received message becomes the one required.
     *
     * @author SquareBoot
     * @version 0.1
     */
    public abstract class ConditionChecker {

        /**
         * Stores the message if it is the one required.
         */
        private String waitedMessage;

        /**
         * @return the required message, if available.
         */
        public String getWaitedMessage() {
            return waitedMessage;
        }

        /**
         * Checks the condition (internal use only). If the message is OK, saves it and terminates the waiting state.
         */
        private void check0(String msg) {
            if (check(msg)) {
                waitedMessage = msg;
                listenersDetach = false;
            }
        }

        /**
         * Method to implement in order to check the condition.
         * Return {@code true} if the given message, which is the one received, is the one required; {@code false} if otherwise.
         *
         * @param msg the input message.
         * @return {@code true} if the message is the one required.
         */
        protected abstract boolean check(String msg);
    }
}