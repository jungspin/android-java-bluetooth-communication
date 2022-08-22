package com.cos.bluetoothsample;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity2";

    private final Context mContext = MainActivity.this;

    private static final int REQUEST_ENABLE_BT = 10; // 블루투스 활성화 상태
    private static final int PERMISSION_CODE = 100;
    private SharedPreferences sharedPreferences;

    private BluetoothAdapter bluetoothAdapter; // 블루투스 어댑터
    private Set<BluetoothDevice> devices; // 블루투스 디바이스 데이터 셋
    private BluetoothDevice bluetoothDevice; // 블루투스 디바이스
    private Thread workerThread = null; // 문자열 수신에 사용되는 쓰레드
    private byte[] readBuffer; // 수신 된 문자열을 저장하기 위한 버퍼
    private int readBufferPosition; // 버퍼 내 문자 저장 위치
    private BroadcastReceiver bluetoothReceiver;

    private TextView textViewReceive; // 수신 된 데이터를 표시하기 위한 텍스트 뷰
    private EditText editTextSend; // 송신 할 데이터를 작성하기 위한 에딧 텍스트
    private Button buttonSend; // 송신하기 위한 버튼
    private Button buttonFind;

    private ActivityResultLauncher<Intent> getResult;

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        initSetting();
        //readyToConnect();
        initListener();
        bluetoothReceiver();
    }

    void init() {
        textViewReceive = findViewById(R.id.textView_receive);
        editTextSend = findViewById(R.id.editText_send);
        buttonSend = findViewById(R.id.button_send);
        buttonFind = findViewById(R.id.button_find);
    }

    void initSetting() {
        // 블루투스 활성화 하기
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter(); // 블루투스 어댑터를 디폴트 어댑터로 설정
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    void initListener(){
        buttonFind.setOnClickListener(v->{
            readyToConnect();
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    void readyToConnect() {
        if (bluetoothAdapter == null) {
            // 블루투스 미지원 단말일 경우
            Toast.makeText(this, "블루투스를 지원하지 않습니다", Toast.LENGTH_SHORT).show();
        } else {
            // 지원 단말일 경우
            if (bluetoothAdapter.isEnabled()) {
                // 블루투스 활성화 상태
                checkPermission();
            } else {
                // 블루투스 비활성화 상태
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                getResult.launch(intent);
                getResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                        (ActivityResult result) -> {
                            if (result.getResultCode() == RESULT_OK) {
                                checkPermission();
                            } else {
                                Toast.makeText(this, "블루투스 연결을 할 수 없습니다", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }
    } // readyToConnect

    /**
     * 해당 기능 사용 시 권한 승인 체크 (매번 확인)
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // need permission
            requestPermissionLauncher.launch(BLUETOOTH_CONNECT);
            if (checkSelfPermission(BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "checkPermission: granted");
                savePermissionDenyTimes(0);
            } else {
                Log.d(TAG, "checkPermission: denied");
                requestPermissions(new String[]{BLUETOOTH_CONNECT}, PERMISSION_CODE);
            }
        } else {
            selectBluetoothDevice();
        }
    }

    /**
     * 권한 요청 응답
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private final ActivityResultLauncher<String> requestPermissionLauncher = (registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                Log.d(TAG, "isGranted: " + isGranted);
                if (isGranted) {
                    savePermissionDenyTimes(0);
                    selectBluetoothDevice();
                } else {
                    savePermissionDenyTimes(1);
                    showDialogForGrant(loadPermissionsDenyTimes() < 2);
                }
            }
    ));


    @SuppressLint("MissingPermission")
    void selectBluetoothDevice() {
        // 이미 페어링 되어있는 블루투스 기기를 찾습니다.
        devices = bluetoothAdapter.getBondedDevices();
        // 페어링 된 디바이스의 크기를 저장
        int pairedDeviceCount = devices.size();
        if (pairedDeviceCount == 0) { // 페어링 되어있는 장치가 없는 경우
            // 페어링을 위한 함수 호출
            Toast.makeText(mContext, "페어링 된 디바이스가 없습니다", Toast.LENGTH_LONG).show();
        } else {
            // 디바이스 선택을 위한 dialog 생성
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("페어링 되어있는 블루투스 디바이스 목록");
            // 페어링된 각각의 디바이스 이름과 주소 저장
            List<String> list = new ArrayList<>();
            //모든 디바이스의 이름을 리스트에 추가
            for (BluetoothDevice bluetoothDevice : devices) {
                list.add(bluetoothDevice.getName());
            }
            list.add("취소");

            // list를 CharSequence 배열로 변경
            final CharSequence[] charSequences = list.toArray(new CharSequence[list.size()]);
            list.toArray(new CharSequence[0]);

            // 해당 아이템을 눌렀을 때 호출 되는 이벤트 리스너
            builder.setItems(charSequences, (dialogInterface, i) -> {
                // 해당 디바이스와 연결하는 함수 호출
                connectDevice(charSequences[i].toString());
            });
            builder.setCancelable(false);

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    @SuppressLint("MissingPermission")
    void connectDevice(String deviceName) {
        // 페어링 된 디바이스들을 모두 탐색
        for (BluetoothDevice tempDevice : devices) {
            // 사용자가 선택한 이름과 같은 디바이스로 설정하고 반복문 종료
            if (deviceName.equals(tempDevice.getName())) {
                bluetoothDevice = tempDevice;
                Log.d(TAG, "connectDevice: " + bluetoothDevice);
                break;
            }
        }
        // UUID 생성
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        // Rfcomm 채널을 통해 블루투스 디바이스와 통신하는 소켓 생성
        try {
            Log.d(TAG, "connectDevice: try");
            // 블루투스 소켓
            BluetoothSocket bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            // 데이터 송, 수신 스트림을 얻어옵니다.
            // 블루투스에 데이터를 출력하기 위한 출력 스트림
            OutputStream outputStream = bluetoothSocket.getOutputStream();
            // 블루투스에 데이터를 입력하기 위한 입력 스트림
            InputStream inputStream = bluetoothSocket.getInputStream();
            // 데이터 수신함수 호출
            // receiveDate();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void bluetoothReceiver() {
        bluetoothReceiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // 장치가 연결이 되었으면
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    Log.d(TAG, "onReceive: ");
                    Toast.makeText(mContext, device.getName() + "is connected", Toast.LENGTH_SHORT).show();

                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
                    registerReceiver(bluetoothReceiver, filter);
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) { // 장치의 연결이 끊겼으면
                    Toast.makeText(mContext, device.getName() + "is disconnected", Toast.LENGTH_SHORT).show();

                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                    registerReceiver(bluetoothReceiver, filter);
                }
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    void showDialogForGrant(boolean isFirst) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        String message;
        if (isFirst) {
            message = "앱을 사용하기 위해서는\n블루투스 권한이 필요합니다.\n권한을 부여하시겠습니까?";
            builder.setPositiveButton("네", (dialogInterface, i) -> {
                requestPermissions(new String[]{BLUETOOTH_CONNECT}, PERMISSION_CODE);
                dialogInterface.dismiss();
            });
            builder.setNegativeButton("아니오", (dialogInterface, i) -> dialogInterface.dismiss());
        } else {
            message = "앱을 사용하기 위해서는\n블루투스 권한이 필요합니다.";
            builder.setPositiveButton("설정으로 이동", (dialogInterface, i) -> {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                dialogInterface.dismiss();
            });
        }
        builder.setMessage(message);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    void savePermissionDenyTimes(int times) {
        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        int num;
        if (times == 0) {
            num = times;
        } else {
            num = loadPermissionsDenyTimes() + 1;
        }

        editor.putInt("times", num);
        editor.apply();
    }

    int loadPermissionsDenyTimes() {
        Log.d(TAG, "loadPermissionsDenyTimes: " + sharedPreferences.getInt("times", 0));
        return sharedPreferences.getInt("times", 0);
    }


}