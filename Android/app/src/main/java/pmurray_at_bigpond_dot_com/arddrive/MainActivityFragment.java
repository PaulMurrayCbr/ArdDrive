package pmurray_at_bigpond_dot_com.arddrive;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;

import static pmurray_at_bigpond_dot_com.arddrive.BluetoothService.startActionSendMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ((Button) getActivity().findViewById(R.id.send_foo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                startActionSendMessage(getActivity().getApplicationContext(), "foo");
            }
        });

        ((SeekBar) getActivity().findViewById(R.id.seekBar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar var1, int var2, boolean var3) {
                startActionSendMessage(getActivity().getApplicationContext(), "SLIDER:" + Integer.toString(var2) + " ;");
            }

            public void onStartTrackingTouch(SeekBar var1) {
            }

            public void onStopTrackingTouch(SeekBar var1) {
            }

        });
    }

}
