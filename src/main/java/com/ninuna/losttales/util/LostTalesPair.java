package com.ninuna.losttales.util;

import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import lotr.common.world.map.LOTRWaypoint;

public class LostTalesPair {
    private final Object key;
    private final Object value;

    public LostTalesPair(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    public LOTRFaction getKeyAsFaction() {
        return (LOTRFaction) key;
    }

    public LOTRWaypoint getKeyAsWaypoint() {
        return (LOTRWaypoint) key;
    }

    public Integer getValueAsInteger() {
        return (Integer) value;
    }

    public LOTRFactionRelations.Relation getValueAsRelation() {
        return (LOTRFactionRelations.Relation) value;
    }
}