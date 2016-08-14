package pmurray_at_bigpond_dot_com.arddrive;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;

import static pmurray_at_bigpond_dot_com.arddrive.BluetoothService.startActionSendMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class DriveNeopixelsFragment extends Fragment {

    byte[] singleByte = new byte[1];

    public DriveNeopixelsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drive_neopixels, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ((SeekBar) getActivity().findViewById(R.id.seekBar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar var1, int var2, boolean var3) {
                singleByte[0] = (byte) var2;
                startActionSendMessage(getActivity().getApplicationContext(), singleByte);
            }

            public void onStartTrackingTouch(SeekBar var1) {
            }

            public void onStopTrackingTouch(SeekBar var1) {
            }

        });
    }

}
