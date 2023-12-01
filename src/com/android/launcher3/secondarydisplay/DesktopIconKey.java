package com.android.launcher3.secondarydisplay;

import com.android.launcher3.util.ComponentKey;
import java.util.Arrays;

public class DesktopIconKey {

    private ComponentKey mKey;

    private int mPos;

    public DesktopIconKey(ComponentKey key,int pos){
        this.mKey = key;
        this.mPos = pos;
    }

    public ComponentKey getComponentKey(){
        return this.mKey;
    }

    public int getPos(){
        return this.mPos;
    }

    public void setPos(int pos) {
        this.mPos = pos;
    }

    @Override
    public boolean equals(Object o) {
        DesktopIconKey other = (DesktopIconKey) o;
        return other.getComponentKey() != null && other.getComponentKey().equals(mKey) && other.getPos() == mPos;
    }

    @Override
    public int hashCode(){
        return Arrays.hashCode(new Object[] {mKey, mPos});
    }
}
