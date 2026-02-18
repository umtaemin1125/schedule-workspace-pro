package com.acme.schedulemanager.domain.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ItemTagId implements Serializable {
    private UUID itemId;
    private UUID tagId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemTagId that)) return false;
        return Objects.equals(itemId, that.itemId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, tagId);
    }
}
