package com.velo.sentinel.context;

import java.lang.ScopedValue;

public final class InferenceContext {
    public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();
}
