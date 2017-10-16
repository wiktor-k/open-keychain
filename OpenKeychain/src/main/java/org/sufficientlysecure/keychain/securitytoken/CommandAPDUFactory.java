package org.sufficientlysecure.keychain.securitytoken;


import java.util.ArrayList;
import java.util.List;

import android.support.annotation.NonNull;

import javax.smartcardio.CommandAPDU;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;


class CommandAPDUFactory {
    private static final int MAX_APDU_NC = 255;
    private static final int MAX_APDU_NC_EXT = 65535;

    private static final int MAX_APDU_NE = 256;
    private static final int MAX_APDU_NE_EXT = 65536;

    private static final int CLA = 0x00;
    private static final int MASK_CLA_CHAINING = 1 << 4;

    private static final int INS_SELECT_FILE = 0xA4;
    private static final int P1_SELECT_FILE = 0x04;
    private static final byte[] AID_SELECT_FILE_OPENPGP = Hex.decode("D27600012401");

    private static final int INS_ACTIVATE_FILE = 0x44;
    private static final int INS_TERMINATE_DF = 0xE6;
    private static final int INS_GET_RESPONSE = 0xC0;

    private static final int INS_INTERNAL_AUTHENTICATE = 0x88;
    private static final int P1_INTERNAL_AUTH_SECURE_MESSAGING = 0x01;

    private static final int INS_VERIFY = 0x20;
    private static final int P2_VERIFY_PW1_SIGN = 0x81;
    private static final int P2_VERIFY_PW1_OTHER = 0x82;
    private static final int P2_VERIFY_PW3 = 0x83;

    private static final int INS_CHANGE_REFERENCE_DATA = 0x24;
    private static final int P2_CHANGE_REFERENCE_DATA_PW1 = 0x81;
    private static final int P2_CHANGE_REFERENCE_DATA_PW3 = 0x83;

    private static final int INS_RESET_RETRY_COUNTER = 0x2C;
    private static final int P1_RESET_RETRY_COUNTER_NEW_PW = 0x02;
    private static final int P2_RESET_RETRY_COUNTER = 0x81;

    private static final int INS_PERFORM_SECURITY_OPERATION = 0x2A;
    private static final int P1_PSO_DECIPHER = 0x80;
    private static final int P1_PSO_COMPUTE_DIGITAL_SIGNATURE = 0x9E;
    private static final int P2_PSO_DECIPHER = 0x86;
    private static final int P2_PSO_COMPUTE_DIGITAL_SIGNATURE = 0x9A;

    private static final int INS_SELECT_DATA = 0xA5;
    private static final int P1_SELECT_DATA_FOURTH = 0x03;
    private static final int P2_SELECT_DATA = 0x04;
    private static final byte[] CP_SELECT_DATA_CARD_HOLDER_CERT = Hex.decode("60045C027F21");

    private static final int INS_GET_DATA = 0xCA;
    private static final int P1_GET_DATA_CARD_HOLDER_CERT = 0x7F;
    private static final int P2_GET_DATA_CARD_HOLDER_CERT = 0x21;

    private static final int INS_PUT_DATA = 0xDA;

    private static final int INS_PUT_DATA_ODD = 0xDB;
    private static final int P1_PUT_DATA_ODD_KEY = 0x3F;
    private static final int P2_PUT_DATA_ODD_KEY = 0xFF;

    private static final int INS_GENERATE_ASYMMETRIC_KEY_PAIR = 0x47;
    private static final int P1_GAKP_GENERATE = 0x80;
    private static final int P1_GAKP_READ_PUBKEY_TEMPLATE = 0x81;
    private static final byte[] CRT_GAKP_SECURE_MESSAGING = Hex.decode("A600");

    private static final int P1_EMPTY = 0x00;
    private static final int P2_EMPTY = 0x00;

    @NonNull
    CommandAPDU createPutDataCommand(int dataObject, byte[] data) {
        return new CommandAPDU(CLA, INS_PUT_DATA, (dataObject & 0xFF00) >> 8, dataObject & 0xFF, data);
    }

    @NonNull
    CommandAPDU createPutKeyCommand(byte[] keyBytes) {
        // the odd PUT DATA INS is for compliance with ISO 7816-8. This is used only to put key data on the card
        return new CommandAPDU(CLA, INS_PUT_DATA_ODD, P1_PUT_DATA_ODD_KEY, P2_PUT_DATA_ODD_KEY, keyBytes);
    }

    @NonNull
    CommandAPDU createComputeDigitalSignatureCommand(byte[] data) {
        return new CommandAPDU(CLA, INS_PERFORM_SECURITY_OPERATION, P1_PSO_COMPUTE_DIGITAL_SIGNATURE,
                P2_PSO_COMPUTE_DIGITAL_SIGNATURE, data, MAX_APDU_NE_EXT);
    }

    @NonNull
    CommandAPDU createDecipherCommand(byte[] data) {
        return new CommandAPDU(CLA, INS_PERFORM_SECURITY_OPERATION, P1_PSO_DECIPHER, P2_PSO_DECIPHER, data,
                MAX_APDU_NE_EXT);
    }

    @NonNull
    CommandAPDU createChangePw1Command(byte[] pin, byte[] newPin) {
        return new CommandAPDU(CLA, INS_CHANGE_REFERENCE_DATA, P1_EMPTY,
                P2_CHANGE_REFERENCE_DATA_PW1, Arrays.concatenate(pin, newPin));
    }

    @NonNull
    CommandAPDU createChangePw3Command(byte[] adminPin, byte[] newAdminPin) {
        return new CommandAPDU(CLA, INS_CHANGE_REFERENCE_DATA, P1_EMPTY,
                P2_CHANGE_REFERENCE_DATA_PW3, Arrays.concatenate(adminPin, newAdminPin));
    }

    @NonNull
    CommandAPDU createResetPw1Command(byte[] newPin) {
        return new CommandAPDU(CLA, INS_RESET_RETRY_COUNTER, P1_RESET_RETRY_COUNTER_NEW_PW,
                P2_RESET_RETRY_COUNTER, newPin);
    }

    @NonNull
    CommandAPDU createGetDataCommand(int p1, int p2) {
        return new CommandAPDU(CLA, INS_GET_DATA, p1, p2, MAX_APDU_NE_EXT);
    }

    @NonNull
    CommandAPDU createGetResponseCommand(int lastResponseSw2) {
        return new CommandAPDU(CLA, INS_GET_RESPONSE, P1_EMPTY, P2_EMPTY, lastResponseSw2);
    }

    @NonNull
    CommandAPDU createVerifyPw1ForSignatureCommand(byte[] pin) {
        return new CommandAPDU(CLA, INS_VERIFY, P1_EMPTY, P2_VERIFY_PW1_SIGN, pin);
    }

    @NonNull
    CommandAPDU createVerifyPw1ForOtherCommand(byte[] pin) {
        return new CommandAPDU(CLA, INS_VERIFY, P1_EMPTY, P2_VERIFY_PW1_OTHER, pin);
    }

    @NonNull
    CommandAPDU createVerifyPw3Command(byte[] pin) {
        return new CommandAPDU(CLA, INS_VERIFY, P1_EMPTY, P2_VERIFY_PW3, pin);
    }

    @NonNull
    CommandAPDU createSelectFileOpenPgpCommand() {
        return new CommandAPDU(CLA, INS_SELECT_FILE, P1_SELECT_FILE, P2_EMPTY, AID_SELECT_FILE_OPENPGP);
    }

    @NonNull
    CommandAPDU createSelectFileCommand(String fileAid) {
        return new CommandAPDU(CLA, INS_SELECT_FILE, P1_SELECT_FILE, P2_EMPTY, Hex.decode(fileAid));
    }

    @NonNull
    CommandAPDU createReactivate2Command() {
        return new CommandAPDU(CLA, INS_ACTIVATE_FILE, P1_EMPTY, P2_EMPTY);
    }

    @NonNull
    CommandAPDU createReactivate1Command() {
        return new CommandAPDU(CLA, INS_TERMINATE_DF, P1_EMPTY, P2_EMPTY);
    }

    @NonNull
    CommandAPDU createInternalAuthForSecureMessagingCommand(byte[] authData) {
        return new CommandAPDU(CLA, INS_INTERNAL_AUTHENTICATE, P1_INTERNAL_AUTH_SECURE_MESSAGING, P2_EMPTY, authData,
                MAX_APDU_NE_EXT);
    }

    @NonNull
    CommandAPDU createGenerateKeyCommand(int slot) {
        return new CommandAPDU(CLA, INS_GENERATE_ASYMMETRIC_KEY_PAIR,
                P1_GAKP_GENERATE, P2_EMPTY, new byte[] { (byte) slot, 0x00 }, MAX_APDU_NE_EXT);
    }

    @NonNull
    CommandAPDU createRetrieveSecureMessagingPublicKeyCommand() {
        // see https://github.com/ANSSI-FR/SmartPGP/blob/master/secure_messaging/smartpgp_sm.pdf
        return new CommandAPDU(CLA, INS_GENERATE_ASYMMETRIC_KEY_PAIR, P1_GAKP_READ_PUBKEY_TEMPLATE, P2_EMPTY,
                CRT_GAKP_SECURE_MESSAGING, MAX_APDU_NE_EXT);
    }

    @NonNull
    CommandAPDU createSelectSecureMessagingCertificateCommand() {
        // see https://github.com/ANSSI-FR/SmartPGP/blob/master/secure_messaging/smartpgp_sm.pdf
        // this command selects the fourth occurence of data tag 7F21
        return new CommandAPDU(CLA, INS_SELECT_DATA, P1_SELECT_DATA_FOURTH, P2_SELECT_DATA,
                CP_SELECT_DATA_CARD_HOLDER_CERT);
    }

    @NonNull
    CommandAPDU createGetDataCardHolderCertCommand() {
        return createGetDataCommand(P1_GET_DATA_CARD_HOLDER_CERT, P2_GET_DATA_CARD_HOLDER_CERT);
    }

    @NonNull
    CommandAPDU createShortApdu(CommandAPDU apdu) {
        int ne = Math.min(apdu.getNe(), MAX_APDU_NE_EXT);
        return new CommandAPDU(apdu.getCLA(), apdu.getINS(), apdu.getP1(), apdu.getP2(), apdu.getData(), ne);
    }

    @NonNull
    List<CommandAPDU> createChainedApdus(CommandAPDU apdu) {
        ArrayList<CommandAPDU> result = new ArrayList<>();

        int offset = 0;
        byte[] data = apdu.getData();
        int ne = Math.min(apdu.getNe(), MAX_APDU_NE_EXT);
        while (offset < data.length) {
            int curLen = Math.min(MAX_APDU_NC, data.length - offset);
            boolean last = offset + curLen >= data.length;
            int cla = apdu.getCLA() + (last ? 0 : MASK_CLA_CHAINING);

            CommandAPDU cmd =
                    new CommandAPDU(cla, apdu.getINS(), apdu.getP1(), apdu.getP2(), data, offset, curLen, ne);
            result.add(cmd);

            offset += curLen;
        }

        return result;
    }

    boolean isSuitableForShortApdu(CommandAPDU apdu) {
        return apdu.getData().length <= MAX_APDU_NC;
    }
}
