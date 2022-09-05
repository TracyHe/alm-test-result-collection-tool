package com.microfocus.mqm.atrf.alm.entities;

import com.microfocus.mqm.atrf.alm.core.AlmEntity;

public class Attachment extends AlmEntity {
    public static String TYPE = "attachment";
    public static String COLLECTION_NAME = "attachments";


    public Attachment() {
        super(TYPE);
    }


}
