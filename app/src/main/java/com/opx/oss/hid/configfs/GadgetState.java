package com.opx.oss.hid.configfs;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class GadgetState {

    public final boolean opxossGadgetExists;
    public final boolean opxossGadgetBound;
    public final String boundUdc;
    public final Set<GadgetFunction> linkedFunctions;
    public final String massStorageFile;

    public GadgetState(boolean opxossGadgetExists,
                       boolean opxossGadgetBound,
                       String boundUdc,
                       Set<GadgetFunction> linkedFunctions,
                       String massStorageFile) {
        this.opxossGadgetExists = opxossGadgetExists;
        this.opxossGadgetBound = opxossGadgetBound;
        this.boundUdc = boundUdc;
        this.linkedFunctions = linkedFunctions == null
                ? Collections.unmodifiableSet(EnumSet.noneOf(GadgetFunction.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(linkedFunctions));
        this.massStorageFile = massStorageFile;
    }
}
