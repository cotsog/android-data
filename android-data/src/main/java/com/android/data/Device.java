package com.android.data;

import org.codehaus.jackson.annotate.JsonCreator;
import org.ektorp.DocumentNotFoundException;

import static android.provider.Settings.Secure;

public class Device extends Document<Device> {
    String ADMIN = "ADMIN";

    @JsonCreator
    public Device() {
    }

    private Device(String id) {
        setId(id);
        shareWith(ADMIN);
    }

    public static void register(DataStore datastore) {
        String deviceId = Secure.getString(datastore.getContext().getContentResolver(), Secure.ANDROID_ID);

        Repository<Device> repo = new Repository<Device>(Device.class, datastore);
        try {
            repo.get(deviceId);
        } catch (DocumentNotFoundException e) {
            repo.add(new Device(deviceId));
        }
    }
}
