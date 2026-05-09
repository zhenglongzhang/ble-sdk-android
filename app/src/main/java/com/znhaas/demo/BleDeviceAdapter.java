package com.znhaas.demo;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.znhaas.demo.databinding.ItemBleDeviceBinding;
import com.znhaas.sdk.model.BleDevice;

import java.util.ArrayList;
import java.util.List;

public class BleDeviceAdapter extends RecyclerView.Adapter<BleDeviceAdapter.DeviceViewHolder> {
    public interface OnDeviceClickListener {
        void onDeviceClick(BleDevice device);
    }

    private final List<BleDevice> devices = new ArrayList<>();
    private final OnDeviceClickListener onDeviceClickListener;

    public BleDeviceAdapter(OnDeviceClickListener onDeviceClickListener) {
        this.onDeviceClickListener = onDeviceClickListener;
    }

    public void clear() {
        devices.clear();
        notifyDataSetChanged();
    }

    public void upsert(BleDevice device) {
        if (device == null) {
            return;
        }
        int existingIndex = findIndexByAddress(device.getAddress());
        if (existingIndex >= 0) {
            devices.set(existingIndex, device);
            notifyItemChanged(existingIndex);
            return;
        }
        devices.add(device);
        notifyItemInserted(devices.size() - 1);
    }

    private int findIndexByAddress(String address) {
        for (int index = 0; index < devices.size(); index++) {
            if (devices.get(index).getAddress().equals(address)) {
                return index;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBleDeviceBinding binding = ItemBleDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new DeviceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        holder.bind(devices.get(position), onDeviceClickListener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private final ItemBleDeviceBinding binding;

        DeviceViewHolder(ItemBleDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(BleDevice device, OnDeviceClickListener listener) {
            String deviceName = device.getDisplayName();
            String rawName = device.getName() == null || device.getName().trim().isEmpty()
                    ? "Unknown device"
                    : device.getName();
            binding.tvDeviceName.setText(deviceName);
            binding.tvDeviceAddress.setText(rawName + "  |  " + device.getAddress());
            binding.tvDeviceMeta.setText("RSSI: " + device.getRssi() + " dBm  |  Bond: " + device.getBondState());
            binding.getRoot().setOnClickListener(view -> listener.onDeviceClick(device));
        }
    }
}
