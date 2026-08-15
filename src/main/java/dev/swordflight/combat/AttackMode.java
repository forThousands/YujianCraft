package dev.swordflight.combat;

public enum AttackMode {
    SORTIE("sortie"),
    RELENTLESS("relentless");

    private final String name;

    AttackMode(String name) {
        this.name = name;
    }

    public String translationKey() {
        return "attack.swordflight." + name;
    }

    public AttackMode next() {
        return this == SORTIE ? RELENTLESS : SORTIE;
    }

    public static AttackMode fromOrdinal(int ordinal) {
        return ordinal == RELENTLESS.ordinal() ? RELENTLESS : SORTIE;
    }
}
