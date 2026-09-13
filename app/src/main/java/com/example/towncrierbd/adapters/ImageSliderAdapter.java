package com.example.towncrierbd.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;

import java.util.List;

public class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.SliderVH> {

    private final List<String> imageUrls;
    private final View.OnClickListener clickListener;

    public ImageSliderAdapter(List<String> imageUrls, View.OnClickListener clickListener) {
        this.imageUrls = imageUrls;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public SliderVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_slider_image, parent, false);
        return new SliderVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SliderVH holder, int position) {
        String url = imageUrls.get(position);
        Glide.with(holder.itemView.getContext())
                .load(url)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivImage);

        if (clickListener != null) {
            holder.itemView.setOnClickListener(clickListener);
        }
    }

    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }

    static class SliderVH extends RecyclerView.ViewHolder {
        ImageView ivImage;
        public SliderVH(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivSliderImage);
        }
    }
}