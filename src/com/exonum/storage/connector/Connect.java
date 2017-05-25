package com.exonum.storage.connector;

public interface Connect {
	
	public void lockWrite();
	public void lockRead();
	public void unlockWrite();
	public void unlockRead();
}