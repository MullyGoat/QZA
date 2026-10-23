package com.qza.custom;

public interface CustomItemState {
    String qzaSkin();

    String qzaUuid();

    void qzaSetSkin(String skin, String uuid);

    int qzaSize();

    void qzaSetSize(int size);

    int qzaOwner();

    void qzaSetOwner(int owner);
}
