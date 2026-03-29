package com.gemstonetrainer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BossType
{
    VERZIK_P2("Verzik P2"),
    SOL_HEREDIT("Sol Heredit");

    private final String displayName;

    @Override
    public String toString()
    {
        return displayName;
    }
}
