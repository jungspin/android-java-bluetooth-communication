package com.cos.bluetoothsample;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity2";

    private Context mContext = MainActivity.this;
    
    private static final int REQUEST_ENABLE_BT = 10; // 블루투스 활성화 상태
    private BluetoothAdapter bluetoothAdapter; // 블루투스 어댑터
    private Set<BluetoothDevice> devices; // 블루투스 디바이스 데이터 셋
    private BluetoothDevice bluetoothDevice; // 블루투스 디바이스
    private BluetoothSocket bluetoothSocket = null; // 블루투스 소켓
    private OutputStream outputStream = null; // 블루투스에 데이터를 출력하기 위한 출력 스트림
    private InputStream inputStream = null; // 블루투스에 데이터를 입력하기 위한 입력 스트림
    private Thread workerThread = null; // 문자열 수신에 사용되는 쓰레드
    private byte[] readBuffer; // 수신 된 문자열을 저장하기 위한 버퍼
    private int readBufferPosition; // 버퍼 내 문자 저장 위치
    private   BroadcastReceiver bluetoothReceiver;

    private TextView textViewReceive; // 수신 된 데이터를 표시하기 위한 텍스트 뷰
    private EditText editTextSend; // 송신 할 데이터를 작성하기 위한 에딧 텍스트
    private Button buttonSend; // 송신하기 위한 버튼

    private ActivityResultLauncher<Intent> getResult;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        settingBluetoothAdapter();
        permissionBluetooth();
        bluetoothReceiver();
    }

    void init(){
        textViewReceive = findViewById(R.id.textView_receive);
        editTextSend = findViewById(R.id.editText_send);
        buttonSend = findViewById(R.id.button_send);
    }

    void settingBluetoothAdapter (){
        // 블루투스 활성화 하기
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter(); // 블루투스 어댑터를 디폴트 어댑터로 설정
    }
    
    void permissionBluetooth(){
       if(bluetoothAdapter == null){
           // 블루투스 미지원 단말일 경우
            Toast.makeText(this, "블루투스를 지원하지 않습니다", Toast.LENGTH_SHORT).show();
       } else {
           // 지원 단말일 경우
           if (bluetoothAdapter.isEnabled()){
               // 블루투스 활성화 상태
               selectBluetoothDevice();
           } else {
               // 블루투스 비활성화 상태
               Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
               getResult.launch(intent);
               getResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                       (ActivityResult result) ->{
                           if (result.getResultCode() == RESULT_OK){
                               selectBluetoothDevice();
                           } else {
                               Toast.makeText(this, "블루투스 연결을 할 수 없습니다", Toast.LENGTH_SHORT).show();
                           }
               });
           }
       }
    } // permissionBluetooth

    @SuppressLint("MissingPermission")
    void selectBluetoothDevice(){
        // 이미 페어링 되어있는 블루투스 기기를 찾습니다.
        devices = bluetoothAdapter.getBondedDevices();
        // 페어링 된 디바이스의 크기를 저장
        int pairedDeviceCount = devices.size();
        if (pairedDeviceCount == 0){ // 페어링 되어있는 장치가 없는 경우
            // 페어링을 위한 함수 호출
        } else {
            // 디바이스 선택을 위한 dialog 생성
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("페어링 되어있는 블루투스 디바이스 목록");
            // 페어링된 각각의 디바이스 이름과 주소 저장
            List<String> list = new ArrayList<>();
            //모든 디바이스의 이름을 리스트에 추가
            for (BluetoothDevice bluetoothDevice : devices){
                list.add(bluetoothDevice.getName());
            }
            list.add("취소");

            // list를 CharSequence 배열로 변경
            final CharSequence[] charSequences = list.toArray(new CharSequence[list.size()]);
            list.toArray(new CharSequence[list.size()]);

            // 해당 아이템을 눌렀을 때 호출 되는 이벤트 리스너
            builder.setItems(charSequences, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    // 해당 디바이스와 연결하는 함수 호출
                    connectDevice(charSequences[i].toString());
                }
            });
            builder.setCancelable(false);

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    @SuppressLint("MissingPermission")
    void connectDevice(String deviceName){
        // 페어링 된 디바이스들을 모두 탐색
        for (BluetoothDevice tempDevice : devices){
            // 사용자가 선택한 이름과 같은 디바이스로 설정하고 반복문 종료
            if (deviceName.equals(tempDevice.getName())){
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
            bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            // 데이터 송, 수신 스트림을 얻어옵니다.
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            // 데이터 수신함수 호출
            // receiveDate();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void bluetoothReceiver(){
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // 장치가 연결이 되었으면
                if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
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


}