package com.hoejmoseit.wingman.wingmanapp.database;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hoejmoseit.wingman.R;

import java.util.List;
import java.util.function.Consumer;

public class SpeechItemAdapter extends RecyclerView.Adapter<SpeechItemAdapter.ViewHolder> {

    private final Consumer<SpeechItem> callback;
    private List<SpeechItem> items;

    public SpeechItemAdapter(List<SpeechItem> items, Consumer<SpeechItem> callback) {
        this.items = items;
        this.callback = callback;

    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.speech_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {


        SpeechItem item = items.get(position);
        holder.titleTextView.setText(item.name);
        holder.textTextView.setText(item.text);
        holder.supportTextView.setText("(DATO)"); // Example support saidText


        holder.itemView.setOnClickListener(v -> {
            if (callback != null) {
                if (position != RecyclerView.NO_POSITION) {

                    // listener.(position, items.get(position));

                    callback.accept(item);

                }
            }
        });
        // Set icon based on item.isFolder (using placeholder icons here)
        holder.iconImageView.setImageResource(item.isFolder ? R.drawable.folder : R.drawable.outline_text_to_speech_24);

        // holder.iconImageView.setImageResource(item.isFolder ? R.drawable.ic_folder : R.drawable.ic_item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView titleTextView;
        public TextView textTextView;
        public TextView supportTextView;
        public ImageView iconImageView;

        public ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.item_title);
            textTextView = itemView.findViewById(R.id.item_text);
            supportTextView = itemView.findViewById(R.id.item_support_text);
            iconImageView = itemView.findViewById(R.id.item_icon);
        }
    }
}