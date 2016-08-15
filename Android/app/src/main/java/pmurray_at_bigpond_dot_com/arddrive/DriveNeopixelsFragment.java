package pmurray_at_bigpond_dot_com.arddrive;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;

import static pmurray_at_bigpond_dot_com.arddrive.BluetoothService.startActionSendMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class DriveNeopixelsFragment extends Fragment {

    byte[] setPos = new byte[] {'P', '\0'};
    byte[] setColor = new byte[] {'C', '\0', '\0', '\0'};

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
                setPos[1] = (byte) var2;
                startActionSendMessage(getActivity().getApplicationContext(), setPos);
            }

            public void onStartTrackingTouch(SeekBar var1) {
            }

            public void onStopTrackingTouch(SeekBar var1) {
            }

        });

        ((ColorPickerView)getActivity().findViewById(R.id.color_picker_view)).addOnColorSelectedListener(new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int selectedColor) {
                setColor[1] = (byte)(selectedColor >> 16);
                setColor[2] = (byte)(selectedColor >> 8);
                setColor[3] = (byte)(selectedColor >> 0);

                startActionSendMessage(getActivity().getApplicationContext(), setColor);
            }
        });

    }

}
