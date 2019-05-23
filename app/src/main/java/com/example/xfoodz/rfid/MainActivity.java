package com.example.xfoodz.rfid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.example.xfoodz.rfid.MVVM.View.NPNHomeView;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.galarzaa.androidthings.Rc522;
import com.example.xfoodz.rfid.MVVM.VM.NPNHomeViewModel;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import org.w3c.dom.Text;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements View.OnClickListener, NPNHomeView, TextToSpeech.OnInitListener {
    private static final String TAG = "NPNIoTs";
    private int DATA_CHECKING = 0;
    private TextToSpeech niceTTS;

    private static final int USB_VENDOR_ID = 0x2341; // 9025
    private static final int USB_PRODUCT_ID = 0x0043;

    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialDevice;
    private String buffer = "";


    //GPIO Configuration Parameters
    private static final String LED_PIN_NAME = "BCM26"; // GPIO port wired to the LED
    private Gpio mLedGpio;

    //SPI Configuration Parameters
    private static final String SPI_DEVICE_NAME = "SPI0.1";
    private SpiDevice mSPIDevice;
    private static final String CS_PIN_NAME = "BCM12"; // GPIO port wired to the LED
    private Gpio mCS;


    // UART Configuration Parameters
    private static final int BAUD_RATE = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;
    private UartDevice mUartDevice;

    byte[] test_data = new byte[]{0,(byte)0x8b,0,0};


    private String DOOR_OPEN = "1";
    private String DOOR_CLOSE = "0";


    public enum DOOR_STATE{
        NONE, WAIT_DOOR_OPEN, WAIT_DOOR_CLOSE, DOOR_OPENED, DOOR_CLOSED
    }
    DOOR_STATE door_state = DOOR_STATE.NONE;
    private int door_timer = 0;
    private int TIME_OUT_DOOR_OPEN = 3;

    private static final int CHUNK_SIZE = 512;

    NPNHomeViewModel mHomeViewModel;
    Timer mBlinkyTimer;

    private TextView txtIPAddress;
    private ImageView imgWifi;

    private TextView time, date;
    private WifiManager wifi;
    private int level = 0;
    private ImageButton back;
    private Handler initTimeAndWifi = new Handler();
    private int idleCount = 0;
    private Handler mIdle = new Handler();
    private boolean idle = false;

    private boolean isAllowProcess = true;

    private TextView textBox;
    private Button butBackLock;
    private Button butSubmitLock;
    private TextView textBoxLock;
    private Button butLock1;
    private Button butLock2;
    private Button butLock3;
    private Button butLock4;

    private Button butOpen;
    private Button butRegister;
    private Button butBackChoose;
    private int regState = 0;

    private String registeredLock = "";

    private Context context = this;
    private boolean standby = false;
    private SpiDevice spiDevice;
    private Gpio gpioReset;
    private static final String SPI_PORT = "SPI0.0";
    private static final String PIN_RESET = "BCM25";
    private Rc522 mRc522;

    private String link = "http://192.168.0.188:3000/api/android/";

    private String currentDoor = "";
    @Override
    public void onSuccessUpdateServer(String message) {
        Log.d(TAG, "Request server is successful " + message);
        if(message.equals("0")){
            standby = true;
            final Dialog errorDialog = new Dialog(context);
            errorDialog.setContentView(R.layout.error_dialog);
            errorDialog.setCancelable(true);
            Button back = errorDialog.findViewById(R.id.buttonBack);
            TextView textView = errorDialog.findViewById(R.id.textBox);

            textView.setText("INVALID CARD!\n\nPLEASE TRY AGAIN!");
            errorDialog.setTitle("");
            errorDialog.show();

            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    errorDialog.cancel();
                    standby = false;
                    textBox.setText("Please swipe your card");
                    startUsbConnection();
                    return;
                }
            });
        }else if (message.equals("1")||message.equals("2")||message.equals("3")||message.equals("4")){
            standby = true;
            final Dialog errorDialog = new Dialog(context);
            errorDialog.setContentView(R.layout.error_dialog);
            errorDialog.setCancelable(true);
            Button back = errorDialog.findViewById(R.id.buttonBack);
            final TextView errorTextBox = errorDialog.findViewById(R.id.textBox);

            errorDialog.setTitle("");
            errorTextBox.setText("SUCCESSFUL!");
            errorDialog.show();

            //message = "1";
            writeUartData(message);
            String speakWords = "Xin vui lòng đến ô số " + message;
//        niceTTS.speak(speakWords, TextToSpeech.QUEUE_FLUSH, null);
            door_state = DOOR_STATE.WAIT_DOOR_OPEN;
            door_timer = TIME_OUT_DOOR_OPEN;
            currentDoor = message;
            readStatus(currentDoor);

            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    errorDialog.cancel();
                    standby = false;
                    textBox.setText("Please swipe your card");
                    startUsbConnection();
                    return;
                }
            });

        }
        else if(message.contains("non-registerd-locker")){
            regState = 2;
            textBoxLock.setText("Please choose your locker");
            c = 0;
            textBoxLock.setVisibility(View.VISIBLE);
            butBackLock.setVisibility(View.VISIBLE);
            butSubmitLock.setVisibility(View.VISIBLE);
            if(message.contains("1")) butLock1.setVisibility(View.VISIBLE);
            if(message.contains("2")) butLock2.setVisibility(View.VISIBLE);
            if(message.contains("3")) butLock3.setVisibility(View.VISIBLE);
            if(message.contains("4")) butLock4.setVisibility(View.VISIBLE);

            textBox.setVisibility(View.GONE);
            butRegister.setVisibility(View.GONE);
            butOpen.setVisibility(View.GONE);
            butBackChoose.setVisibility(View.GONE);
            standby = true;
        }
        else if(message.contains("registered-locker")){
            registeredLock = "";
            if(message.contains("1")) registeredLock += "1";
            if(message.contains("2")) registeredLock += "2";
            if(message.contains("3")) registeredLock += "3";
            if(message.contains("4")) registeredLock += "4";
            Log.d("debug", "availableLock: " + registeredLock);
            textBox.setVisibility(View.GONE);
            butOpen.setVisibility(View.VISIBLE);
            butRegister.setVisibility(View.VISIBLE);
            butBackChoose.setVisibility(View.VISIBLE);
            stopUsbConnection();
            standby = true;

        }
    }

    public void talkToMe(String sentence) {
        String speakWords = sentence;
        niceTTS.speak(speakWords, TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override
    public void onErrorUpdateServer(String message) {
        //txtConsole.setText("Request server is fail");
        Log.d(TAG, "Request server is fail");
        standby = true;
        final Dialog errorDialog = new Dialog(context);
        errorDialog.setContentView(R.layout.error_dialog);
        errorDialog.setCancelable(true);
        Button back = errorDialog.findViewById(R.id.buttonBack);
        TextView textView = errorDialog.findViewById(R.id.textBox);

        textView.setText("CAN'T REACH THE SERVER!");
        errorDialog.setTitle("");
        errorDialog.show();

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                errorDialog.cancel();
                standby = false;
                textBox.setText("Please swipe your card");
                startUsbConnection();
                return;
            }
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //do they have the data
        if (requestCode == DATA_CHECKING) {
            //yep - go ahead and instantiate
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)
                niceTTS = new TextToSpeech(this, this);
                //no data, prompt to install it
            else {
                Intent promptInstall = new Intent();
                promptInstall.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(promptInstall);
            }
        }
    }

    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            niceTTS.setLanguage(Locale.forLanguageTag("VI"));
        }
    }

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        imgWifi = findViewById(R.id.imgWifi);
        txtIPAddress = findViewById(R.id.txtIPAddress);

        time = findViewById(R.id.time);
        date = findViewById(R.id.date);
        back = findViewById(R.id.back);
        initTimeAndWifi.post(initTimeAndWifiRunnable);
        mIdle.post(mIdleRunnable);

        mHomeViewModel = new NPNHomeViewModel();
        mHomeViewModel.attach(this, this);

        initGPIO();
        initUart();
        initSPI();
        setupBlinkyTimer();

        //create an Intent
        Intent checkData = new Intent();
        //set it up to check for tts data
        checkData.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        //start it so that it returns the result
        startActivityForResult(checkData, DATA_CHECKING);

//        visibleAllControls(false);


        Ultis.writeToInternalFile("test.txt", "abcdefgh");
        String readTest = Ultis.readFromInternalFile("test.txt");
        Log.d(TAG, "Data is: " + readTest);

        back.setOnClickListener(this);

        textBox = findViewById(R.id.textBox);

        textBoxLock = findViewById(R.id.textBoxLock);
        butBackLock = findViewById(R.id.buttonBackLock);
        butSubmitLock = findViewById(R.id.buttonSubmit);
        butLock1 = findViewById(R.id.button1);
        butLock2 = findViewById(R.id.button2);
        butLock3 = findViewById(R.id.button3);
        butLock4 = findViewById(R.id.button4);
        butBackLock.setOnClickListener(this);
        butSubmitLock.setOnClickListener(this);
        butLock1.setOnClickListener(this);
        butLock2.setOnClickListener(this);
        butLock3.setOnClickListener(this);
        butLock4.setOnClickListener(this);

        butOpen = findViewById(R.id.butOpen);
        butRegister = findViewById(R.id.butRegister);
        butBackChoose = findViewById(R.id.butBackChoose);
        butOpen.setOnClickListener(this);
        butRegister.setOnClickListener(this);
        butBackChoose.setOnClickListener(this);

        usbManager = getSystemService(UsbManager.class);

        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        startUsbConnection();
//        mReadHandler.post(read);
    }

    @Override
    public void onClick(View view) {
        if(view == back){
            Intent LaunchIntent = context.getPackageManager().getLaunchIntentForPackage("com.example.xfoodz.home");
            if(LaunchIntent != null) {
                context.startActivity(LaunchIntent);
                finish();
                System.exit(0);
            }
        }
        else if(view == butBackLock){
            if(regState == 0){
                textBoxLock.setText("Please choose your locker");
                c = 0;
                textBoxLock.setVisibility(View.GONE);
                butBackLock.setVisibility(View.GONE);
                butSubmitLock.setVisibility(View.GONE);
                butLock1.setVisibility(View.GONE);
                butLock2.setVisibility(View.GONE);
                butLock3.setVisibility(View.GONE);
                butLock4.setVisibility(View.GONE);

                butOpen.setVisibility(View.VISIBLE);
                butRegister.setVisibility(View.VISIBLE);
                butBackChoose.setVisibility(View.VISIBLE);
                standby = true;
            }
            else if(regState == 1){
                textBoxLock.setText("Please choose your locker");
                c = 0;
                textBoxLock.setVisibility(View.GONE);
                butBackLock.setVisibility(View.GONE);
                butSubmitLock.setVisibility(View.GONE);
                butLock1.setVisibility(View.GONE);
                butLock2.setVisibility(View.GONE);
                butLock3.setVisibility(View.GONE);
                butLock4.setVisibility(View.GONE);

                butRegister.setVisibility(View.VISIBLE);
                butOpen.setVisibility(View.VISIBLE);
                butBackChoose.setVisibility(View.VISIBLE);
                standby = true;
            }
            else{
                textBox.setVisibility(View.VISIBLE);

                textBoxLock.setVisibility(View.GONE);
                butBackLock.setVisibility(View.GONE);
                butSubmitLock.setVisibility(View.GONE);
                butLock1.setVisibility(View.GONE);
                butLock2.setVisibility(View.GONE);
                butLock3.setVisibility(View.GONE);
                butLock4.setVisibility(View.GONE);
                standby = false;
                startUsbConnection();
            }
        }
        else if(view == butSubmitLock){
            if(c == 0){
                Toast.makeText(context, "Invalid locker!", Toast.LENGTH_SHORT).show();
            }
            else {
                System.out.println("a " + a);
                System.out.println("b " + b);
                System.out.println("number " + c);
                String url = "";
                if (regState == 0) {
                    url = link + "rfid/open?locker=" + c;
                    Log.d("debug", "open");
                } else {
                    url = link + "rfid/register?name=" + a + "&pass=" + b + "&locker=" + c;
                    Log.d("debug", "register");
                }
                mHomeViewModel.updateToServer(url);
                door_state = DOOR_STATE.DOOR_CLOSED;

                textBox.setText("Processing...");
                textBox.setVisibility(View.VISIBLE);

                textBoxLock.setVisibility(View.GONE);
                butBackLock.setVisibility(View.GONE);
                butSubmitLock.setVisibility(View.GONE);
                butLock1.setVisibility(View.GONE);
                butLock2.setVisibility(View.GONE);
                butLock3.setVisibility(View.GONE);
                butLock4.setVisibility(View.GONE);
            }

        }
        else if(view == butLock1){
            if(regState == 0){
                textBoxLock.setText("You choose to OPEN Locker 1");
            }
            else textBoxLock.setText("You choose to REGISTER Locker 1");
            c = 1;
        }
        else if(view == butLock2){
            if(regState == 0){
                textBoxLock.setText("You choose to OPEN Locker 2");
            }
            else textBoxLock.setText("You choose to REGISTER Locker 2");
            c = 2;
        }
        else if(view == butLock3){
            if(regState == 0){
                textBoxLock.setText("You choose to OPEN Locker 3");
            }
            else textBoxLock.setText("You choose to REGISTER Locker 3");
            c = 3;
        }
        else if(view == butLock4){
            if(regState == 0){
                textBoxLock.setText("You choose to OPEN Locker 4");
            }
            else textBoxLock.setText("You choose to REGISTER Locker 4");
            c = 4;
        }
        else if(view == butOpen){
            regState = 0;
            textBoxLock.setText("Please choose your locker");
            c = 0;
            textBoxLock.setVisibility(View.VISIBLE);
            butBackLock.setVisibility(View.VISIBLE);
            butSubmitLock.setVisibility(View.VISIBLE);
            if(registeredLock.contains("1")) butLock1.setVisibility(View.VISIBLE);
            if(registeredLock.contains("2")) butLock2.setVisibility(View.VISIBLE);
            if(registeredLock.contains("3")) butLock3.setVisibility(View.VISIBLE);
            if(registeredLock.contains("4")) butLock4.setVisibility(View.VISIBLE);

            butRegister.setVisibility(View.GONE);
            butOpen.setVisibility(View.GONE);
            butBackChoose.setVisibility(View.GONE);
            standby = true;
        }
        else if(view == butRegister){
            regState = 1;
            textBoxLock.setText("Please choose your locker");
            c = 0;
            textBoxLock.setVisibility(View.VISIBLE);
            butBackLock.setVisibility(View.VISIBLE);
            butSubmitLock.setVisibility(View.VISIBLE);
            if(!registeredLock.contains("1")) butLock1.setVisibility(View.VISIBLE);
            if(!registeredLock.contains("2")) butLock2.setVisibility(View.VISIBLE);
            if(!registeredLock.contains("3")) butLock3.setVisibility(View.VISIBLE);
            if(!registeredLock.contains("4")) butLock4.setVisibility(View.VISIBLE);

            butRegister.setVisibility(View.GONE);
            butOpen.setVisibility(View.GONE);
            butBackChoose.setVisibility(View.GONE);
            standby = true;
        }
        else if(view == butBackChoose){
            textBox.setText("Please swipe your card");
            textBox.setVisibility(View.VISIBLE);

            butRegister.setVisibility(View.GONE);
            butOpen.setVisibility(View.GONE);
            butBackChoose.setVisibility(View.GONE);
        }
    }

    private void startUsbConnection() {
        Map<String, UsbDevice> connectedDevices = usbManager.getDeviceList();

        if (!connectedDevices.isEmpty()) {
            for (UsbDevice device : connectedDevices.values()) {
                if (device.getVendorId() == USB_VENDOR_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "Device found: " + device.getDeviceName());
                    startSerialConnection(device);
                    return;
                }
            }
        }
        Log.w(TAG, "Could not start USB connection - No devices found");
    }

    private void startSerialConnection(UsbDevice device) {
        Log.i(TAG, "Ready to open USB device connection");
        connection = usbManager.openDevice(device);
        serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (serialDevice != null) {
            if (serialDevice.open()) {
                serialDevice.setBaudRate(9600);
                serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialDevice.read(callback);
                Log.i(TAG, "Serial connection opened");
            } else {
                Log.w(TAG, "Cannot open serial connection");
            }
        } else {
            Log.w(TAG, "Could not create Usb Serial Device");
        }
    }

    String a;
    String b;
    int c;

    private void onSerialDataReceived(String data) {
        // Add whatever you want here
//        dataView.setText(data);
        Log.i(TAG, "Serial data received: " + data);

        String[] words = data.split("\\s");
        //for (String w : words) {
        //  System.out.println(w);
        //}
        System.out.println("aaaaaaaa " + words[0]);
        System.out.println("bbbbbbbb " + words[1]);

        a = words[0];
        b = words[1];

        if(data != null){
            //Log.d(TAG, txtPinCode.getText().toString());
            String url = link + "rfid?name=" + words[0] + "&pass=" + words[1];
            mHomeViewModel.updateToServer(url);
            //txtPinCode.setText("");
            door_state = DOOR_STATE.DOOR_CLOSED;
            //}
            return;
        }else{
            Log.d(TAG, "NO DATA RECEIVED");
        }
    }

    private void stopUsbConnection() {
        try {
            if (serialDevice != null) {
                serialDevice.close();
            }

            if (connection != null) {
                connection.close();
            }
        } finally {
            serialDevice = null;
            connection = null;
        }
    }

    private UsbSerialInterface.UsbReadCallback callback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            try {
                String dataUtf8 = new String(data, "UTF-8");
                buffer += dataUtf8;
                int index;
                while ((index = buffer.indexOf('\n')) != -1) {
                    final String dataStr = buffer.substring(0, index + 1).trim();
                    buffer = buffer.length() == index ? "" : buffer.substring(index + 1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onSerialDataReceived(dataStr);
                        }
                    });
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Error receiving USB data", e);

            }
        }
    };

    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.getVendorId() == USB_VENDOR_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "USB device detached");
                    stopUsbConnection();
                }
            }
        }
    };

    private int counterWifi = 0;
    private void setupBlinkyTimer()
    {
        mBlinkyTimer = new Timer();
        TimerTask blinkyTask = new TimerTask() {
            @Override
            public void run() {
                counterWifi++;

                if(counterWifi >= 5) {
                    counterWifi = 0;
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                            int numberOfLevels = 5;
                            WifiInfo wifiInfo = wifi.getConnectionInfo();
                            level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);
                            if(wifi.isWifiEnabled() == false) level = -1;
                            if(isEthernetConnected()) level = -2;
                            switch(level){
                                case 0:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_0_bar_black_48dp);
                                    break;
                                case 1:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_1_bar_black_48dp);
                                    break;
                                case 2:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_2_bar_black_48dp);
                                    break;
                                case 3:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_3_bar_black_48dp);
                                    break;
                                case 4:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_4_bar_black_48dp);
                                    break;
                                case -1:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_off_bar_black_48dp);
                                    break;
                                case -2:
                                    imgWifi.setImageResource(R.drawable.ic_computer_black_24dp);
                                    break;
                            }
                            txtIPAddress = findViewById(R.id.txtIPAddress);
                            if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                                int ipAddress = wifiInfo.getIpAddress();
                                String ipString = Formatter.formatIpAddress(ipAddress);
                                txtIPAddress.setText(ipString);
                            } else txtIPAddress.setText("No connection");

                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
                            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
                            date.setText(format.format(new Date()));
                            format = new SimpleDateFormat("hh:mm");
                            time.setText(format.format(new Date()));

                        }
                    });

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mLedGpio.setValue(!mLedGpio.getValue());

                            } catch (Throwable t) {
                                Log.d(TAG, "Error in Blinky LED " + t.getMessage());
                            }
                        }
                    });
                }
                //readStatus("1");
                switch (door_state){
                    case NONE:
                        if(door_timer > 0)
                        {
                            door_timer--;
                            if(door_timer == 0){
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
//                                        visibleAllControls(false);
                                        isAllowProcess = true;
                                    }
                                });

                            }
                        }
                        break;
                    case WAIT_DOOR_OPEN:
                        door_timer--;
                        if(door_status.equals(DOOR_OPEN) == true){
                            door_state = DOOR_STATE.DOOR_OPENED;
                        }else {
                            readStatus(currentDoor);
                        }
                        if(door_timer == 0)
                        {
                            Log.d("NPNIoTs", "Open again the door: " + currentDoor);
                            writeUartData(currentDoor);
                            door_timer = 3;
                        }
                        break;
                    case DOOR_OPENED:
                        door_timer = 10;
                        readStatus(currentDoor);
                        door_state = DOOR_STATE.WAIT_DOOR_CLOSE;
                        break;
                    case WAIT_DOOR_CLOSE:
                        door_timer--;
                        readStatus(currentDoor);
                        if(door_status.equals(DOOR_CLOSE)){
                            door_state = DOOR_STATE.DOOR_CLOSED;
                        }
                        if(door_timer <= 0)
                        {
                            talkToMe("Xin vui lòng đóng cửa số " + currentDoor);
                            door_timer = 5;
                        }
                        break;
                    case DOOR_CLOSED:
                        talkToMe("Xin cám ơn quý khách");
                        door_state = DOOR_STATE.NONE;
                        door_timer = 5;
                        break;
                    default:
                        break;
                }
            }
        };
        mBlinkyTimer.schedule(blinkyTask, 5000, 1000);
    }

    public void writeUartData(String message) {
        try {
            byte[] buffer = {'W',' ',' '};
            buffer[2] =  (byte)(Integer.parseInt(message));
            int count = mUartDevice.write(buffer, buffer.length);
            Log.d(TAG, "Send: "   + buffer[2]);
        }catch (IOException e)
        {
            Log.d(TAG, "Error on UART");
        }
    }

    public void readStatus(String ID)
    {
        try {
            byte[] buffer = {'R',' ',' '};
            buffer[2] =  (byte)(Integer.parseInt(ID));
            int count = mUartDevice.write(buffer, buffer.length);
            //Log.d(TAG, "Wrote " + count + " bytes to peripheral  "  + buffer[2]);
        }catch (IOException e)
        {
            Log.d(TAG, "Error on UART");
        }
    }

    private void initSPI()
    {
        PeripheralManager manager = PeripheralManager.getInstance();
        List<String> deviceList = manager.getSpiBusList();
        if(deviceList.isEmpty())
        {
            Log.d(TAG,"No SPI bus is not available");
        }
        else
        {
            Log.d(TAG,"SPI bus available: " + deviceList);
            //check if SPI_DEVICE_NAME is in list
            try {
                mSPIDevice = manager.openSpiDevice(SPI_DEVICE_NAME);

                mSPIDevice.setMode(SpiDevice.MODE1);
                mSPIDevice.setFrequency(1000000);
                mSPIDevice.setBitsPerWord(8);
                mSPIDevice.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);


                Log.d(TAG,"SPI: OK... ");


            }catch (IOException e)
            {
                Log.d(TAG,"Open SPI bus fail... ");
            }
        }
    }

    private void sendCommand(SpiDevice device, byte[] buffer) throws  IOException{


        mCS.setValue(false);
        for(int i = 0; i < 100; i++) {}

        //send data to slave
        device.write(buffer, buffer.length);


        //read the response
        byte[] response = new byte[2];
        device.read(response, response.length);


        for(int i = 0; i< 2; i++) {

            Log.d(TAG, "Response byte " + Integer.toString(i) + " is: " + response[i]);
        }
        mCS.setValue(true);
        for(int i = 0; i < 100; i++){}

        double value = (double)(response[0] * 256 + response[1]);
        double adc = value * 6.144/32768;


    }

    private void initGPIO()
    {
        PeripheralManager manager = PeripheralManager.getInstance();
        try {
            mLedGpio = manager.openGpio(LED_PIN_NAME);
            // Step 2. Configure as an output.
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            mCS = manager.openGpio(CS_PIN_NAME);
            mCS.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);

            spiDevice = manager.openSpiDevice(SPI_PORT);
            gpioReset = manager.openGpio(PIN_RESET);
            mRc522 = new Rc522(spiDevice, gpioReset);
            mRc522.setDebugging(true);
        } catch (IOException e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            Log.d(TAG, "Error on PeripheralIO API");
        }
    }

    private void initUart()
    {
        try {
            openUart("UART0", BAUD_RATE);
        }catch (IOException e) {
            Log.d(TAG, "Error on UART API");
        }
    }
    /**
     * Callback invoked when UART receives new incoming data.
     */
    private String door_status = "";
    private UartDeviceCallback mCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            //read data from Rx buffer
            try {
                byte[] buffer = new byte[CHUNK_SIZE];
                int noBytes = -1;
                while ((noBytes = mUartDevice.read(buffer, buffer.length)) > 0) {
                    Log.d(TAG,"Number of bytes: " + Integer.toString(noBytes));

                    String str = new String(buffer,0,noBytes, "UTF-8");

                    Log.d(TAG,"Buffer is: " + str);
                    door_status = str;

                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to transfer data over UART", e);
            }
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };

    private void openUart(String name, int baudRate) throws IOException {
        mUartDevice = PeripheralManager.getInstance().openUartDevice(name);
        // Configure the UART
        mUartDevice.setBaudrate(baudRate);
        mUartDevice.setDataSize(DATA_BITS);
        mUartDevice.setParity(UartDevice.PARITY_NONE);
        mUartDevice.setStopBits(STOP_BITS);

        mUartDevice.registerUartDeviceCallback(mCallback);
    }

    private void closeUart() throws IOException {
        if (mUartDevice != null) {
            mUartDevice.unregisterUartDeviceCallback(mCallback);
            try {
                mUartDevice.close();
            } finally {
                mUartDevice = null;
            }
        }
    }

    private void closeSPI() throws IOException {
        if(mSPIDevice != null)
        {
            try {
                mSPIDevice.close();
            }finally {
                mSPIDevice = null;
            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Attempt to close the UART device
        try {
            stopUsbConnection();

            closeUart();
            mUartDevice.unregisterUartDeviceCallback(mCallback);
            closeSPI();
        } catch (IOException e) {
            Log.e(TAG, "Error closing UART device:", e);
        }
    }

    private Runnable initTimeAndWifiRunnable = new Runnable() {
        @Override
        public void run() {
            wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int numberOfLevels = 5;
            WifiInfo wifiInfo = wifi.getConnectionInfo();
            level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);
            if(wifi.isWifiEnabled() == false) level = -1;
            if(isEthernetConnected()) level = -2;
            switch(level){
                case 0:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_0_bar_black_48dp);
                    break;
                case 1:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_1_bar_black_48dp);
                    break;
                case 2:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_2_bar_black_48dp);
                    break;
                case 3:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_3_bar_black_48dp);
                    break;
                case 4:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_4_bar_black_48dp);
                    break;
                case -1:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_off_bar_black_48dp);
                    break;
                case -2:
                    imgWifi.setImageResource(R.drawable.ic_computer_black_24dp);
                    break;
            }
            txtIPAddress = findViewById(R.id.txtIPAddress);
            if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                int ipAddress = wifiInfo.getIpAddress();
                String ipString = Formatter.formatIpAddress(ipAddress);
                txtIPAddress.setText(ipString);
            } else txtIPAddress.setText("No connection");

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
            date.setText(format.format(new Date()));
            format = new SimpleDateFormat("hh:mm");
            time.setText(format.format(new Date()));
        }
    };

    private Runnable mIdleRunnable = new Runnable() {
        @Override
        public void run() {
            if(idleCount == 30) {
                idle = true;
                idleCount = 0;
                Intent LaunchIntent = context.getPackageManager().getLaunchIntentForPackage("com.example.xfoodz.home");
                if(LaunchIntent != null) {
                    context.startActivity(LaunchIntent);
                    finish();
                    System.exit(0);
                }
            }
            else {
                if(standby == false) idleCount++;
                else idleCount = 0;
            }
            if(!idle) mIdle.postDelayed(mIdleRunnable, 1000);
        }
    };

    private Boolean isNetworkAvailable() {
        ConnectivityManager cm
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    public Boolean isEthernetConnected(){
        if(isNetworkAvailable()){
            ConnectivityManager cm
                    = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            return (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET);
        }
        return false;
    }
}