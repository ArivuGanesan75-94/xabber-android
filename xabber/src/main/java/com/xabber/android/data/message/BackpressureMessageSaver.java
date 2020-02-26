package com.xabber.android.data.message;

import android.os.Looper;

import com.xabber.android.data.Application;
import com.xabber.android.data.SettingsManager;
import com.xabber.android.data.database.DatabaseManager;
import com.xabber.android.data.database.realmobjects.Attachment;
import com.xabber.android.data.database.realmobjects.MessageItem;
import com.xabber.android.data.filedownload.DownloadManager;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.push.SyncManager;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subjects.PublishSubject;


/** Groups messages to save. It is necessary to avoid
 * java.util.concurrent.RejectedExecutionException error,
 * which can occur if you frequently save messages one at a time in Realm.
 *
 * Issue in crashlytics: https://www.fabric.io/redsolution/android/apps/com.xabber.android/issues/5c55e9edf8b88c29636f3fd3
 * */
public class BackpressureMessageSaver {

    private static final String LOG_TAG = BackpressureMessageSaver.class.getSimpleName();

    private static BackpressureMessageSaver instance;
    private PublishSubject<MessageItem> subject;

    public static BackpressureMessageSaver getInstance() {
        if (instance == null) instance = new BackpressureMessageSaver();
        return instance;
    }

    public void saveMessageItem(MessageItem messageItem) {
        if (hasCopyInRealm(messageItem)) return;
        subject.onNext(messageItem);
    }

    private BackpressureMessageSaver() {
        createSubject();
    }

    private void createSubject() {
        subject = PublishSubject.create();
        subject.buffer(250, TimeUnit.MILLISECONDS)
            .onBackpressureBuffer()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Action1<List<MessageItem>>() {
                @Override
                public void call(final List<MessageItem> messageItems) {
                    if (messageItems == null || messageItems.isEmpty()) return;
                    Realm realm = null;
                    try {
                        realm = DatabaseManager.getInstance().getDefaultRealmInstance();
                        realm.executeTransactionAsync(realm1 -> {
                            realm1.copyToRealm(messageItems);
                        }, () ->  {
                            EventBus.getDefault().post(new NewMessageEvent());
                            SyncManager.getInstance().onMessageSaved();
                            checkForAttachmentsAndDownload(messageItems);
                        });
                    } catch (Exception e) {
                        LogManager.exception(this, e);
                    } finally { if ( realm != null && Looper.myLooper() != Looper.getMainLooper())
                        realm.close(); }
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    LogManager.exception(this, throwable);
                    LogManager.d(this, "Exception is thrown. Created new publish subject.");
                    createSubject();
                }
            });
    }

    //TODO refactor this method before releasing
    private boolean hasCopyInRealm(final MessageItem newIncomingMessageItem){
        boolean result = false;
        Realm realm = DatabaseManager.getInstance().getDefaultRealmInstance();
        MessageItem item;

        if (newIncomingMessageItem.getUniqueId() != null) {
            item = realm.where(MessageItem.class)
                    .equalTo(MessageItem.Fields.UNIQUE_ID, newIncomingMessageItem.getUniqueId())
                    .equalTo(MessageItem.Fields.ACCOUNT, newIncomingMessageItem.getAccount().toString())
                    .findFirst();
            if (item != null && !newIncomingMessageItem.isForwarded()) {
                result = true;
                LogManager.d(LOG_TAG,
                        "Received message, but we already have message with same ID! \n Message stanza: "
                                + newIncomingMessageItem.getOriginalStanza() + "\nMessage already in database stanza: "
                                + item.getOriginalStanza());
            }
        }

        if (newIncomingMessageItem.getStanzaId() != null) {
            item = realm.where(MessageItem.class)
                    .equalTo(MessageItem.Fields.STANZA_ID, newIncomingMessageItem.getStanzaId())
                    .equalTo(MessageItem.Fields.ACCOUNT, newIncomingMessageItem.getAccount().toString())
                    .findFirst();
            if (item != null && !newIncomingMessageItem.isForwarded()) {
                result = true;
                LogManager.d(LOG_TAG,
                        "Received message, but we already have message with same ID! \n Message stanza: "
                                + newIncomingMessageItem.getOriginalStanza() + "\nMessage already in database stanza: "
                                + item.getOriginalStanza());
            }
        }

        if (newIncomingMessageItem.getOriginId() != null) {
            item = realm.where(MessageItem.class)
                    .equalTo(MessageItem.Fields.ORIGIN_ID, newIncomingMessageItem.getOriginId())
                    .equalTo(MessageItem.Fields.ACCOUNT, newIncomingMessageItem.getAccount().toString())
                    .findFirst();
            if (item != null && !newIncomingMessageItem.isForwarded()) {
                result = true;
                LogManager.d(LOG_TAG,
                        "Received message, but we already have message with same ID! \n Message stanza: "
                                + newIncomingMessageItem.getOriginalStanza() + "\nMessage already in database stanza: "
                                + item.getOriginalStanza());
            }
        }

        if (Looper.myLooper() != Looper.getMainLooper()) realm.close();

        return result;
    }

    private void checkForAttachmentsAndDownload(List<MessageItem> messageItems) {
        if (SettingsManager.chatsAutoDownloadVoiceMessage()) {
            for (MessageItem message : messageItems) {
                if (message.haveAttachments()) {
                    for (Attachment attachment : message.getAttachments()) {
                        if (attachment.isVoice() && attachment.getFilePath() == null) {
                            DownloadManager.getInstance().downloadFile(attachment, message.getAccount(), Application.getInstance());
                        }
                    }
                }
            }
        }
    }
}
