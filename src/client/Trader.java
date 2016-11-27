package client;


import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Trader extends Remote {

    void callback(String message) throws RemoteException;
    String getClientName() throws RemoteException;
}
