import React from 'react';
import {Pressable, Text, View} from 'react-native';

import {DialerCall, formatDialerCallState, formatDialerRoute} from '../../SmsGateway';
import {styles} from '../../styles/appStyles';

const DTMF_ROWS = [
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

export function InCallScreen({
  call,
  digits,
  dialpadVisible,
  onToggleDialpad,
  onPressDigit,
  onBackspace,
  onAnswerCall,
  onRejectCall,
  onEndCall,
  onToggleMute,
  onShowSystemUi,
  onReturnToCalls,
}: {
  call: DialerCall | null;
  digits: string;
  dialpadVisible: boolean;
  onToggleDialpad: () => void;
  onPressDigit: (digit: string) => void;
  onBackspace: () => void;
  onAnswerCall: (call: DialerCall) => void;
  onRejectCall: (call: DialerCall) => void;
  onEndCall: (call: DialerCall) => void;
  onToggleMute: (call: DialerCall) => void;
  onShowSystemUi: (showDialpad?: boolean) => void;
  onReturnToCalls: () => void;
}) {
  const title = call?.displayName || call?.number || 'Unknown caller';
  const subtitle =
    call?.number && call.displayName !== call.number
      ? call.number
      : call?.direction === 'incoming'
        ? 'Incoming mobile'
        : call?.direction === 'outgoing'
          ? 'Mobile'
          : 'Phone';
  const statusBits = call
    ? [formatDialerCallState(call), formatDialerRoute(call.route)].filter(Boolean)
    : [];
  const initials = getInitials(title);
  const canSendDtmf = call?.state === 'active';

  return (
    <View style={styles.inCallScreen}>
      <View style={styles.inCallGlowLarge} />
      <View style={styles.inCallGlowSmall} />

      <View style={styles.inCallTopRow}>
        <Pressable
          accessibilityRole="button"
          onPress={onReturnToCalls}
          style={styles.inCallTopButton}>
          <Text style={styles.inCallTopButtonText}>Calls</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          onPress={() => onShowSystemUi(dialpadVisible)}
          style={styles.inCallTopButton}>
          <Text style={styles.inCallTopButtonText}>System UI</Text>
        </Pressable>
      </View>

      <View style={styles.inCallHero}>
        <View style={styles.inCallAvatar}>
          <Text style={styles.inCallAvatarText}>{initials}</Text>
        </View>
        <Text style={styles.inCallName}>{title}</Text>
        <Text style={styles.inCallSubtitle}>{subtitle}</Text>
        {statusBits.length ? (
          <Text style={styles.inCallStatus}>{statusBits.join('  /  ')}</Text>
        ) : (
          <Text style={styles.inCallStatus}>No active call</Text>
        )}
        {digits ? <Text style={styles.inCallDigits}>{digits}</Text> : null}
      </View>

      {call ? (
        <>
          {dialpadVisible ? (
            <View style={styles.inCallPanel}>
              <Text style={styles.inCallPanelTitle}>Touch tones</Text>
              <Text style={styles.inCallPanelCopy}>
                {canSendDtmf
                  ? 'Tap digits to send DTMF through the live call.'
                  : 'The keypad will wake up once the call is live.'}
              </Text>
              <View style={styles.inCallKeypad}>
                {DTMF_ROWS.map(row => (
                  <View key={row.map(item => item.digit).join('')} style={styles.inCallKeypadRow}>
                    {row.map(item => (
                      <Pressable
                        key={item.digit}
                        accessibilityRole="button"
                        disabled={!canSendDtmf}
                        onPress={() => onPressDigit(item.digit)}
                        style={[
                          styles.inCallKey,
                          !canSendDtmf ? styles.inCallKeyDisabled : null,
                        ]}>
                        <Text style={styles.inCallKeyDigit}>{item.digit}</Text>
                        <Text style={styles.inCallKeyLetters}>{item.letters}</Text>
                      </Pressable>
                    ))}
                  </View>
                ))}
              </View>
              <View style={styles.inCallSmallActionRow}>
                <Pressable
                  accessibilityRole="button"
                  onPress={onBackspace}
                  style={styles.inCallTextAction}>
                  <Text style={styles.inCallTextActionLabel}>Backspace</Text>
                </Pressable>
                <Pressable
                  accessibilityRole="button"
                  onPress={onToggleDialpad}
                  style={styles.inCallTextAction}>
                  <Text style={styles.inCallTextActionLabel}>Hide keypad</Text>
                </Pressable>
              </View>
            </View>
          ) : (
            <View style={styles.inCallPanel}>
              <Text style={styles.inCallPanelTitle}>Call controls</Text>
              <Text style={styles.inCallPanelCopy}>
                A focused in-call surface for answering, muting, keypad entry, and handoff to
                Android&apos;s system UI.
              </Text>
              <View style={styles.inCallActionGrid}>
                <CallActionTile
                  icon={<MicGlyph muted={call.isMuted} active={call.isMuted} />}
                  label={call.isMuted ? 'Unmute' : 'Mute'}
                  tone={call.isMuted ? 'active' : 'neutral'}
                  onPress={() => onToggleMute(call)}
                />
                <CallActionTile
                  icon={<KeypadGlyph active={dialpadVisible} />}
                  label="Keypad"
                  tone={dialpadVisible ? 'active' : 'neutral'}
                  onPress={onToggleDialpad}
                />
                <CallActionTile
                  icon={<RouteGlyph route={call.route} />}
                  label={formatDialerRoute(call.route)}
                  tone="neutral"
                  onPress={() => onShowSystemUi(false)}
                />
                <CallActionTile
                  icon={<ScreenGlyph />}
                  label="Android UI"
                  tone="neutral"
                  onPress={() => onShowSystemUi(false)}
                />
              </View>
            </View>
          )}

          <View style={styles.inCallFooter}>
            {call.canReject ? (
              <CallPrimaryButton
                label="Decline"
                tone="danger"
                onPress={() => onRejectCall(call)}
              />
            ) : null}
            {call.canAnswer ? (
              <CallPrimaryButton
                label="Answer"
                tone="success"
                onPress={() => onAnswerCall(call)}
              />
            ) : null}
            {call.canDisconnect ? (
              <CallPrimaryButton label="End" tone="danger" onPress={() => onEndCall(call)} />
            ) : null}
          </View>
        </>
      ) : (
        <View style={styles.inCallPanel}>
          <Text style={styles.inCallPanelTitle}>Call ended</Text>
          <Text style={styles.inCallPanelCopy}>
            The in-call screen is ready, but there is no active Telecom call at the moment.
          </Text>
          <View style={styles.inCallSmallActionRow}>
            <Pressable
              accessibilityRole="button"
              onPress={onReturnToCalls}
              style={styles.inCallTextAction}>
              <Text style={styles.inCallTextActionLabel}>Back to calls</Text>
            </Pressable>
          </View>
        </View>
      )}
    </View>
  );
}

function CallActionTile({
  icon,
  label,
  tone,
  onPress,
}: {
  icon: React.ReactNode;
  label: string;
  tone: 'neutral' | 'active';
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={[
        styles.inCallActionTile,
        tone === 'active' ? styles.inCallActionTileActive : null,
      ]}>
      <View
        style={[
          styles.inCallActionIconWrap,
          tone === 'active' ? styles.inCallActionIconWrapActive : null,
        ]}>
        {icon}
      </View>
      <Text
        style={[
          styles.inCallActionLabel,
          tone === 'active' ? styles.inCallActionLabelActive : null,
        ]}>
        {label}
      </Text>
    </Pressable>
  );
}

function MicGlyph({
  muted,
  active,
}: {
  muted: boolean;
  active: boolean;
}) {
  return (
    <View style={styles.inCallGlyphBase}>
      <View
        style={[
          styles.inCallMicBody,
          active ? styles.inCallGlyphAccent : styles.inCallGlyphNeutral,
        ]}
      />
      <View
        style={[
          styles.inCallMicStem,
          active ? styles.inCallGlyphAccent : styles.inCallGlyphNeutral,
        ]}
      />
      <View
        style={[
          styles.inCallMicFoot,
          active ? styles.inCallGlyphAccent : styles.inCallGlyphNeutral,
        ]}
      />
      {muted ? <View style={styles.inCallMuteSlash} /> : null}
    </View>
  );
}

function KeypadGlyph({active}: {active: boolean}) {
  return (
    <View style={styles.inCallKeypadGlyph}>
      {Array.from({length: 9}).map((_, index) => (
        <View
          key={index}
          style={[
            styles.inCallKeypadGlyphDot,
            active ? styles.inCallGlyphAccentFill : styles.inCallGlyphNeutralFill,
          ]}
        />
      ))}
    </View>
  );
}

function RouteGlyph({route}: {route: string}) {
  return (
    <View style={styles.inCallRouteBadge}>
      <Text style={styles.inCallRouteBadgeText}>{routeLabel(route)}</Text>
    </View>
  );
}

function ScreenGlyph() {
  return (
    <View style={styles.inCallScreenGlyph}>
      <View style={styles.inCallScreenGlyphInner} />
      <View style={styles.inCallScreenGlyphBar} />
    </View>
  );
}

function CallPrimaryButton({
  label,
  tone,
  onPress,
}: {
  label: string;
  tone: 'success' | 'danger';
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={[
        styles.inCallPrimaryButton,
        tone === 'success' ? styles.inCallPrimaryButtonSuccess : styles.inCallPrimaryButtonDanger,
      ]}>
      <Text style={styles.inCallPrimaryButtonText}>{label}</Text>
    </Pressable>
  );
}

function getInitials(value: string): string {
  const compact = value.trim();
  if (!compact) {
    return '?';
  }

  const words = compact.split(/\s+/).filter(Boolean);
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }

  return `${words[0][0] || ''}${words[1][0] || ''}`.toUpperCase();
}

function routeLabel(route: string): string {
  switch (route) {
    case 'bluetooth':
      return 'BT';
    case 'speaker':
      return 'SPK';
    case 'earpiece':
      return 'EAR';
    case 'wired_headset':
      return 'AUX';
    case 'streaming':
      return 'STR';
    default:
      return 'TEL';
  }
}
