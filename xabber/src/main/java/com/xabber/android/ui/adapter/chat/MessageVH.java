package com.xabber.android.ui.adapter.chat;

import android.content.Context;
import android.support.annotation.StyleRes;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.xabber.android.R;
import com.xabber.android.data.database.messagerealm.MessageItem;
import com.xabber.android.data.extension.otr.OTRManager;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.utils.StringUtils;

import java.util.Date;

public class MessageVH extends BasicMessageVH implements View.OnClickListener, View.OnLongClickListener {

    private static final String LOG_TAG = MessageVH.class.getSimpleName();
    private MessageClickListener listener;
    private MessageLongClickListener longClickListener;

    TextView messageTime;
    TextView messageHeader;
    TextView messageNotDecrypted;
    View messageBalloon;
    ImageView statusIcon;
    ImageView ivEncrypted;
    CheckBox checkbox;
    String messageId;

    public interface MessageClickListener {
        void onMessageClick(View caller, int position);
    }

    public interface MessageLongClickListener {
        void onLongMessageClick(int position);
    }

    public MessageVH(View itemView, MessageClickListener listener,
                     MessageLongClickListener longClickListener, @StyleRes int appearance) {
        super(itemView, appearance);
        this.listener = listener;
        this.longClickListener = longClickListener;

        messageTime = itemView.findViewById(R.id.message_time);
        messageHeader = itemView.findViewById(R.id.message_header);
        messageNotDecrypted = itemView.findViewById(R.id.message_not_decrypted);
        messageBalloon = itemView.findViewById(R.id.message_balloon);
        statusIcon = itemView.findViewById(R.id.message_status_icon);
        ivEncrypted = itemView.findViewById(R.id.message_encrypted_icon);
        checkbox = itemView.findViewById(R.id.checkbox);

        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);
    }

    public void bind(MessageItem messageItem, boolean isMUC, boolean showOriginalOTR,
                     Context context, boolean unread, boolean checked, boolean showCheckBoxes) {
        if (isMUC) {
            messageHeader.setText(messageItem.getResource());
            messageHeader.setVisibility(View.VISIBLE);
        } else {
            messageHeader.setVisibility(View.GONE);
        }

        if (messageItem.isEncrypted()) {
            ivEncrypted.setVisibility(View.VISIBLE);
        } else {
            ivEncrypted.setVisibility(View.GONE);
        }

        messageText.setText(messageItem.getText());
        if (OTRManager.getInstance().isEncrypted(messageItem.getText())) {
            if (showOriginalOTR)
                messageText.setVisibility(View.VISIBLE);
            else messageText.setVisibility(View.GONE);
            messageNotDecrypted.setVisibility(View.VISIBLE);
        } else {
            messageText.setVisibility(View.VISIBLE);
            messageNotDecrypted.setVisibility(View.GONE);
        }

        String time = StringUtils.getTimeText(new Date(messageItem.getTimestamp()));

        Long delayTimestamp = messageItem.getDelayTimestamp();
        if (delayTimestamp != null) {
            String delay = context.getString(messageItem.isIncoming() ? R.string.chat_delay : R.string.chat_typed,
                    StringUtils.getTimeText(new Date(delayTimestamp)));
            time += " (" + delay + ")";
        }

        messageTime.setText(time);

        // setup UNREAD
        if (unread) itemView.setBackgroundColor(context.getResources().getColor(R.color.unread_messages_background));
        else itemView.setBackgroundDrawable(null);

        // setup CHECKED
        checkbox.setChecked(checked);

        // setup CHECKBOX VISIBILITY
        checkbox.setVisibility(showCheckBoxes ? View.VISIBLE : View.GONE);

    }

    @Override
    public void onClick(View v) {
        int adapterPosition = getAdapterPosition();
        if (adapterPosition == RecyclerView.NO_POSITION) {
            LogManager.w(LOG_TAG, "onClick: no position");
            return;
        }
        listener.onMessageClick(messageBalloon, adapterPosition);
    }

    @Override
    public boolean onLongClick(View v) {
        int adapterPosition = getAdapterPosition();
        if (adapterPosition == RecyclerView.NO_POSITION) {
            LogManager.w(LOG_TAG, "onClick: no position");
            return false;
        }
        longClickListener.onLongMessageClick(adapterPosition);
        return true;
    }
}
