package dji.sdk.matrice210.Interfaces;

public interface MocInteractionListener {
    void sendData(final String data);
    void sendData(final byte[] data);
}
