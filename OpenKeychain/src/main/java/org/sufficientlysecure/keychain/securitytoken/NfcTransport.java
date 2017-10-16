/*
 * Copyright (C) 2016 Nikita Mikhailov <nikita.s.mikhailov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.securitytoken;

import android.nfc.Tag;

import javax.smartcardio.CommandAPDU;
import org.sufficientlysecure.keychain.ui.base.BaseSecurityTokenActivity;

import java.io.IOException;

import nordpol.IsoCard;
import nordpol.android.AndroidCard;

public class NfcTransport implements Transport {
    // timeout is set to 100 seconds to avoid cancellation during calculation
    private static final int TIMEOUT = 100 * 1000;
    private final Tag mTag;
    private IsoCard mIsoCard;

    public NfcTransport(Tag tag) {
        this.mTag = tag;
    }

    /**
     * Transmit and receive data
     * @param data data to transmit
     * @return received data
     * @throws IOException
     */
    @Override
    public ResponseApdu transceive(final CommandAPDU data) throws IOException {
        return ResponseApdu.fromBytes(mIsoCard.transceive(data.getBytes()));
    }

    /**
     * Disconnect and release connection
     */
    @Override
    public void release() {
        // Not supported
    }

    @Override
    public boolean isConnected() {
        return mIsoCard != null && mIsoCard.isConnected();
    }

    /**
     * Check if Transport supports persistent connections e.g connections which can
     * handle multiple operations in one session
     * @return true if transport supports persistent connections
     */
    @Override
    public boolean isPersistentConnectionAllowed() {
        return false;
    }

    /**
     * Connect to NFC device.
     * <p/>
     * On general communication, see also
     * http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4_annex-a.aspx
     * <p/>
     * References to pages are generally related to the OpenPGP Application
     * on ISO SmartCard Systems specification.
     */
    @Override
    public void connect() throws IOException {
        mIsoCard = AndroidCard.get(mTag);
        if (mIsoCard == null) {
            throw new BaseSecurityTokenActivity.IsoDepNotSupportedException("Tag does not support ISO-DEP (ISO 14443-4)");
        }

        mIsoCard.setTimeout(TIMEOUT);
        mIsoCard.connect();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final NfcTransport that = (NfcTransport) o;

        if (mTag != null ? !mTag.equals(that.mTag) : that.mTag != null) return false;
        if (mIsoCard != null ? !mIsoCard.equals(that.mIsoCard) : that.mIsoCard != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = mTag != null ? mTag.hashCode() : 0;
        result = 31 * result + (mIsoCard != null ? mIsoCard.hashCode() : 0);
        return result;
    }
}
