package com.childmonitor.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.childmonitor.services.CallSmsService;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        try {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null) return;

            String format = bundle.getString("format");
            StringBuilder fullMessage = new StringBuilder();
            String senderNumber = "";

            for (Object pdu : pdus) {
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                fullMessage.append(smsMessage.getMessageBody());
                senderNumber = smsMessage.getOriginatingAddress();
            }

            Intent serviceIntent = new Intent(context, CallSmsService.class);
            serviceIntent.putExtra("new_sms", true);
            serviceIntent.putExtra("sms_number", senderNumber);
            serviceIntent.putExtra("sms_body", fullMessage.toString());
            context.startService(serviceIntent);

        } catch (Exception e) {
            // ignore
        }
    }
}
