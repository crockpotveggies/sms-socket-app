import React from 'react';
import {Pressable, Text, View} from 'react-native';

import {GatewayStatus} from '../../SmsGateway';
import {styles} from '../../styles/appStyles';

const DIAL_PAD_ROWS = [
  [
    {digit: '1', letters: ''},
    {digit: '2', letters: 'ABC'},
    {digit: '3', letters: 'DEF'},
  ],
  [
    {digit: '4', letters: 'GHI'},
    {digit: '5', letters: 'JKL'},
    {digit: '6', letters: 'MNO'},
  ],
  [
    {digit: '7', letters: 'PQRS'},
    {digit: '8', letters: 'TUV'},
    {digit: '9', letters: 'WXYZ'},
  ],
  [
    {digit: '*', letters: ''},
    {digit: '0', letters: '+'},
    {digit: '#', letters: ''},
  ],
];

export function DialerScreen({
  status,
  number,
  onPressDigit,
  onBackspace,
  onPlaceCall,
  onRequestRole,
  onRequestPermissions,
}: {
  status: GatewayStatus | null;
  number: string;
  onChangeNumber: (value: string) => void;
  onPressDigit: (digit: string) => void;
  onBackspace: () => void;
  onPlaceCall: () => void;
  onRequestRole: () => void;
  onRequestPermissions: () => void;
}) {
  const dialerControlReady =
    Boolean(status?.dialerRoleGranted) &&
    !status?.dialerMissingPermissions.some(
      permission => permission !== 'android.permission.READ_CALL_LOG',
    );
  const showPermissionStrip =
    status?.dialerRoleGranted === false || Boolean(status?.dialerMissingPermissions.length);

  return (
    <View style={styles.dialerScreen}>
      <View style={styles.dialerGlowLarge} />
      <View style={styles.dialerGlowSmall} />

      {showPermissionStrip ? (
        <View style={styles.dialerAlertCard}>
          <Text style={styles.dialerAlertTitle}>Dialer setup still needs attention</Text>
          <View style={styles.dialerAlertActions}>
            {status?.dialerRoleGranted === false ? (
              <Pressable
                accessibilityRole="button"
                onPress={onRequestRole}
                style={styles.dialerAlertButton}>
                <Text style={styles.dialerAlertButtonText}>Request role</Text>
              </Pressable>
            ) : null}
            {Boolean(status?.dialerMissingPermissions.length) ? (
              <Pressable
                accessibilityRole="button"
                onPress={onRequestPermissions}
                style={styles.dialerAlertButton}>
                <Text style={styles.dialerAlertButtonText}>Permissions</Text>
              </Pressable>
            ) : null}
          </View>
        </View>
      ) : null}

      <View style={styles.dialerNumberCard}>
        <Text style={number.trim() ? styles.dialerNumber : styles.dialerNumberPlaceholder}>
          {number.trim() || 'Enter number'}
        </Text>
        <Text style={styles.dialerNumberHint}>
          {dialerControlReady
            ? 'Ready to place a PSTN call'
            : 'Dialer controls unlock once role and permissions are ready'}
        </Text>
      </View>

      <View style={styles.dialerPadCard}>
        <View style={styles.dialerPadGrid}>
          {DIAL_PAD_ROWS.map(row => (
            <View key={row.map(item => item.digit).join('')} style={styles.dialerPadRowLarge}>
              {row.map(item => (
                <Pressable
                  key={item.digit}
                  accessibilityRole="button"
                  onPress={() => onPressDigit(item.digit)}
                  style={styles.dialerPadKeyLarge}>
                  <Text style={styles.dialerPadKeyDigit}>{item.digit}</Text>
                  <Text style={styles.dialerPadKeyLetters}>{item.letters}</Text>
                </Pressable>
              ))}
            </View>
          ))}
        </View>

        <View style={styles.dialerBottomRow}>
          <Pressable
            accessibilityRole="button"
            onPress={onBackspace}
            style={styles.dialerBackspaceButton}>
            <Text style={styles.dialerBackspaceButtonText}>Backspace</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            disabled={!dialerControlReady || number.trim().length === 0}
            onPress={onPlaceCall}
            style={[
              styles.dialerCallButton,
              !dialerControlReady || number.trim().length === 0
                ? styles.dialerCallButtonDisabled
                : null,
            ]}>
            <PhoneGlyph color="#ffffff" />
          </Pressable>
          <View style={styles.dialerBottomSpacer} />
        </View>
      </View>
    </View>
  );
}

function PhoneGlyph({color}: {color: string}) {
  return (
    <View style={styles.dialerPhoneGlyph}>
      <View style={[styles.dialerPhoneGlyphArc, {borderColor: color}]} />
      <View style={[styles.dialerPhoneGlyphHandle, {backgroundColor: color}]} />
    </View>
  );
}
