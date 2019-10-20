package com.example.miband;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class DeviceCandidateAdapter extends ArrayAdapter<MiBandDevice> {

    private final Context context;

    public DeviceCandidateAdapter(Context context, List<MiBandDevice> deviceCandidates) {
        super(context, 0, deviceCandidates);

        this.context = context;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        MiBandDevice device = getItem(position);

        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            view = inflater.inflate(R.layout.item_with_details, parent, false);
        }
        ImageView deviceImageView = (ImageView) view.findViewById(R.id.item_image);
        TextView deviceNameLabel = (TextView) view.findViewById(R.id.item_name);
        TextView deviceAddressLabel = (TextView) view.findViewById(R.id.item_details);

        String name = formatDeviceCandidate(device);
        deviceNameLabel.setText(name);
        deviceAddressLabel.setText(device.getAddress() + " | " + device.getName());
        deviceImageView.setImageResource(R.drawable.ic_miband_background);

        return view;
    }

    private String formatDeviceCandidate(MiBandDevice device) {
        return device.getName();
    }
}