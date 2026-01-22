package dji.sdk.matrice210.Fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dji.sdk.matrice210.Interfaces.MocInteraction;
import dji.sdk.matrice210.Interfaces.MocInteractionListener;
import dji.sdk.matrice210.R;
import dji.sdk.matrice210.tools.ByteArrayUtils;
import dji.common.error.DJIError;

public class MissionFragment extends Fragment implements Observer, View.OnClickListener, MocInteraction {

    // UI Elements
    private TextView txtView_console, txtView_antenna;
    private EditText editTxt_command, editTxt_x, editTxt_y, editTxt_z, editTxt_yaw;

    // Antenna
    char loading[] = {'-', '\\', '|', '/' };    // used to display a loading char
    int antennaLoadingIndex = 0;

    private enum M210_MissionType {
        VELOCITY(1),
        POSITION(2),
        POSITION_OFFSET(3),
        WAYPOINTS(4);

        private final int value;
        M210_MissionType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    private enum M210_MissionAction {
        START(1),
        ADD(2),
        RESET(3),
        STOP(4),
        PAUSE(5),
        RESUME(6)
        ;

        private final int value;
        M210_MissionAction(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // Listener
    private MocInteractionListener mocIListener;

    @Override
    public void dataReceived(byte[] bytes) {
        String data = ByteArrayUtils.byteArrayToString(bytes);

        // Regex are used to find specified format
        // () group data

        // Log message are formatted as follow
        // [moc_log_char][type][message]
        // type is D (debug), E (error) or S (status)
        // message is a string
        Pattern p = Pattern.compile("^" + getString(R.string.moc_log_char) + "([DES])(.*)$");
        Matcher m = p.matcher(data);
        if (m.find()) {
            MatchResult mr = m.toMatchResult();
            Map<Character, String> prefixMap = new HashMap<>();
            prefixMap.put('S', "STATUS");
            prefixMap.put('D', "DEBUG");
            prefixMap.put('E', "ERROR");
            // Get display type from char type
            char type = mr.group(1).charAt(0);
            String prefix = prefixMap.get(type);
            log(mr.group(2), prefix);
            return;
        }

        // Antenna message are formatted as follow
        // [moc_command_antenna][value in float (4 bytes)]
        p = Pattern.compile("^" + getString(R.string.moc_command_antenna));
        m = p.matcher(data);
        if (m.find() && bytes.length == 6) {
            // Get 4 bytes of float value
            byte[] valueArray = new byte[4];
            System.arraycopy(bytes, 2, valueArray, 0, 4);
            // Cpp and java doesn't have the same endianness
            ByteArrayUtils.reverseEndianness(valueArray);
            int value = ByteArrayUtils.byteArrayToInt(valueArray);
            // Update dedicated text view with antenna value
            updateAntennaValue(value);
            return;
        }

        // Display message as string
        log(data, "MOC");
    }

    @Override
    public void onResult(DJIError djiError) {
        if (djiError != null)
            log("Error " + djiError.toString(), "MOC");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mission_layout, container, false);
        // Aircraft actions
        view.findViewById(R.id.btn_takeOff).setOnClickListener(this);
        view.findViewById(R.id.btn_landing).setOnClickListener(this);
        view.findViewById(R.id.btn_stopMission).setOnClickListener(this);
        // Console
        txtView_console = (TextView) view.findViewById(R.id.txtView_console);
        txtView_console.setMovementMethod(new ScrollingMovementMethod());
        // Moc
        editTxt_command = (EditText) view.findViewById(R.id.editTxt_command);
        view.findViewById(R.id.btn_send).setOnClickListener(this);
        // Position - Velocity mission
        editTxt_x = (EditText) view.findViewById(R.id.editTxt_x);
        editTxt_y = (EditText) view.findViewById(R.id.editTxt_y);
        editTxt_z = (EditText) view.findViewById(R.id.editTxt_z);
        editTxt_yaw = (EditText) view.findViewById(R.id.editTxt_yaw);
        view.findViewById(R.id.btn_position).setOnClickListener(this);
        view.findViewById(R.id.btn_velocity).setOnClickListener(this);
        // Waypoint mission
        view.findViewById(R.id.btn_waypoints_add).setOnClickListener(this);
        view.findViewById(R.id.btn_waypoints_start).setOnClickListener(this);
        view.findViewById(R.id.btn_waypoints_clear).setOnClickListener(this);
        view.findViewById(R.id.btn_waypoints_pause).setOnClickListener(this);
        view.findViewById(R.id.btn_waypoints_resume).setOnClickListener(this);
        view.findViewById(R.id.btn_waypoints_stop).setOnClickListener(this);
        // Antenna
        txtView_antenna = (TextView) view.findViewById(R.id.txtView_antenna);
        // Emergency and control authority
        view.findViewById(R.id.btn_obtainControlAuthority).setOnClickListener(this);
        view.findViewById(R.id.btn_releaseEmergency).setOnClickListener(this);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        try {
            mocIListener = (MocInteractionListener) activity;
        } catch (ClassCastException e) {
            e.printStackTrace();
            throw new ClassCastException(activity.toString()
                    + " must implement MocInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void update(Observable observable, Object o) {
        // Try catch cause the fragment is not necessary visible
        try {

        } catch (Exception e) {
        }
    }

    public void sendMocData(final String data) {
        log("Send data (" + data.length() + ") : " + data, "MOC");
        mocIListener.sendData(data);
    }

    public void sendMocData(final byte[] data) {
        if((char)data[0] == getString(R.string.moc_command_char).charAt(0))
            log("Send command (" + data.length + ") : " + ByteArrayUtils.byteArrayToString(data), "MOC");
        else
            log("Send data (" + data.length + ") : " + ByteArrayUtils.byteArrayToString(data), "MOC");
        mocIListener.sendData(data);
    }

    public void log(final String log) {
        log(log, "LOG");
    }

    public void log(final String log, final String prefix) {
        log(log, prefix, false);
    }

    public void log(final String log, final String prefix, final boolean clear) {
        // runOnUiThread used to avoid errors
        // "Only the original thread that created a view hierarchy can touch its views"
        try{
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    DateFormat df = new SimpleDateFormat("[HH:mm:ss]");
                    String time = df.format(Calendar.getInstance().getTime());
                    String line = time + " - " + prefix + " - " + log;
                    if(clear) {
                        txtView_console.setText(line);
                    } else {
                        txtView_console.setText(line.concat("\n").concat(txtView_console.getText().toString()));
                    }
                }
            });
        } catch (NullPointerException e) {
            // Avoid null pointer exception
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_takeOff:
                sendMocData(getString(R.string.moc_command_takeoff));
                break;
            case R.id.btn_landing:
                sendMocData(getString(R.string.moc_command_landing));
                break;
            case R.id.btn_stopMission:
                sendMocData(getString(R.string.moc_command_stopMission));
                break;
            case R.id.btn_send:
                final String command = editTxt_command.getText().toString();
                sendMocData(command);
                break;
            // todo replace redundant code by function
            case R.id.btn_position: {
                float x = readFloatFromEditText(editTxt_x);
                float y = readFloatFromEditText(editTxt_y);
                float z = readFloatFromEditText(editTxt_z);
                float yaw = readFloatFromEditText(editTxt_yaw);

                byte[] xB = ByteArrayUtils.float2ByteArray(x);
                byte[] yB = ByteArrayUtils.float2ByteArray(y);
                byte[] zB = ByteArrayUtils.float2ByteArray(z);
                byte[] yawB = ByteArrayUtils.float2ByteArray(yaw);

                // Java and C++ float representation have inverse endianness
                ByteArrayUtils.reverseEndianness(xB);
                ByteArrayUtils.reverseEndianness(yB);
                ByteArrayUtils.reverseEndianness(zB);
                ByteArrayUtils.reverseEndianness(yawB);

                // Frame
                String mission_command = getString(R.string.moc_command_mission);
                byte[] buffer = new byte[20];
                buffer[0] = (byte)mission_command.charAt(0);    // command char
                buffer[1] = (byte)mission_command.charAt(1);    // mission char
                buffer[2] = (byte)M210_MissionType.POSITION_OFFSET.value();
                buffer[3] = (byte)M210_MissionAction.START.getValue();
                System.arraycopy(xB, 0, buffer, 4, 4);      // x
                System.arraycopy(yB, 0, buffer, 8, 4);      // y
                System.arraycopy(zB, 0, buffer, 12, 4);     // z
                System.arraycopy(yawB, 0, buffer, 16, 4);   // yaw

                log("Position offset mission : " + String.valueOf(x) + ", " + String.valueOf(y) + ", " + String.valueOf(z) + ", " + String.valueOf(yaw));
                sendMocData(buffer);
            }
            break;
            case R.id.btn_velocity: {
                float x = readFloatFromEditText(editTxt_x);
                float y = readFloatFromEditText(editTxt_y);
                float z = readFloatFromEditText(editTxt_z);
                float yaw = readFloatFromEditText(editTxt_yaw);

                byte[] xB = ByteArrayUtils.float2ByteArray(x);
                byte[] yB = ByteArrayUtils.float2ByteArray(y);
                byte[] zB = ByteArrayUtils.float2ByteArray(z);
                byte[] yawB = ByteArrayUtils.float2ByteArray(yaw);

                // Java and C++ float representation have inverse endianness
                ByteArrayUtils.reverseEndianness(xB);
                ByteArrayUtils.reverseEndianness(yB);
                ByteArrayUtils.reverseEndianness(zB);
                ByteArrayUtils.reverseEndianness(yawB);

                // Frame
                String mission_command = getString(R.string.moc_command_mission);
                byte[] buffer = new byte[20];
                buffer[0] = (byte)mission_command.charAt(0);    // command char
                buffer[1] = (byte)mission_command.charAt(1);    // mission char
                buffer[2] = (byte)M210_MissionType.VELOCITY.value();
                buffer[3] = (byte)M210_MissionAction.START.getValue();
                System.arraycopy(xB, 0, buffer, 4, 4);      // x
                System.arraycopy(yB, 0, buffer, 8, 4);      // y
                System.arraycopy(zB, 0, buffer, 12, 4);     // z
                System.arraycopy(yawB, 0, buffer, 16, 4);   // yaw

                log("Velocity mission : " + String.valueOf(x) + ", " + String.valueOf(y) + ", " + String.valueOf(z) + ", " + String.valueOf(yaw));
                sendMocData(buffer);
            }
            break;
            case R.id.btn_waypoints_add:
                // Send add waypoint request frame
                sendWaypointsMissionAction(M210_MissionAction.ADD);
                log("Waypoints mission - Current position saved");
                break;
            case R.id.btn_waypoints_start:
                // Send start waypoint request frame
                sendWaypointsMissionAction(M210_MissionAction.START);
                log("Waypoints mission - Start");
                break;
            case R.id.btn_waypoints_clear:
                // Send clear waypoint request frame
                sendWaypointsMissionAction(M210_MissionAction.RESET);
                log("Waypoints mission - Clear waypoints");
                break;
            case R.id.btn_waypoints_pause:
                // Send clear waypoint request frame
                sendWaypointsMissionAction(M210_MissionAction.PAUSE);
                log("Waypoints mission - Pause");
                break;
            case R.id.btn_waypoints_resume:
                sendWaypointsMissionAction(M210_MissionAction.RESUME);
                log("Waypoints mission - Resume");
                break;
            case R.id.btn_waypoints_stop:
                sendWaypointsMissionAction(M210_MissionAction.STOP);
                log("Waypoints mission - Stop");
                break;
            case R.id.btn_obtainControlAuthority:
                sendMocData(getString(R.string.moc_command_obtainControlAuthority));
                break;
            case R.id.btn_releaseEmergency:
                sendMocData(getString(R.string.moc_command_emergencyRelease));
                break;
        }
    }

    private void sendWaypointsMissionAction(M210_MissionAction action) {
        String mission_command = getString(R.string.moc_command_mission);
        byte[] buffer = new byte[4];
        buffer[0] = (byte)mission_command.charAt(0);    // command char
        buffer[1] = (byte)mission_command.charAt(1);    // mission char
        buffer[2] = (byte)M210_MissionType.WAYPOINTS.value();
        buffer[3] = (byte)action.getValue();
        sendMocData(buffer);
    }

    private float readFloatFromEditText(EditText editText) {
        float val = 0;
        try {
            val = Float.valueOf(editText.getText().toString());
        } catch (Exception e){
            toast("Invalid float !");
        }
        return val;
    }

    public void updateAntennaValue(final int value) {
        // runOnUiThread used to avoid errors
        // "Only the original thread that created a view hierarchy can touch its views"
        try{
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String text = getString(R.string.mission_antenna, loading[antennaLoadingIndex], value);
                    txtView_antenna.setText(text);
                    antennaLoadingIndex = (++antennaLoadingIndex)%4;
                }
            });
        } catch (NullPointerException e) {
            // Avoid null pointer exception
        }
    }

    public void toast(final String text)
    {
        (getActivity()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                System.out.println(text);
                Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
