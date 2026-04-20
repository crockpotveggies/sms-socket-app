import React from 'react';
import {Pressable, Text, View} from 'react-native';

import {styles} from '../styles/appStyles';
import {CallsTabIcon, GatewayTabIcon, MessagesTabIcon} from './Icons';

export function BottomTabBar({
  active,
  onChange,
}: {
  active: 'messages' | 'calls' | 'gateway';
  onChange: (tab: 'messages' | 'calls' | 'gateway') => void;
}) {
  return (
    <View style={styles.bottomBar}>
      <BottomTabButton
        label="Messages"
        active={active === 'messages'}
        icon={<MessagesTabIcon active={active === 'messages'} />}
        onPress={() => onChange('messages')}
      />
      <BottomTabButton
        label="Calls"
        active={active === 'calls'}
        icon={<CallsTabIcon active={active === 'calls'} />}
        onPress={() => onChange('calls')}
      />
      <BottomTabButton
        label="Gateway"
        active={active === 'gateway'}
        icon={<GatewayTabIcon active={active === 'gateway'} />}
        onPress={() => onChange('gateway')}
      />
    </View>
  );
}

function BottomTabButton({
  label,
  active,
  icon,
  onPress,
}: {
  label: string;
  active: boolean;
  icon: React.ReactNode;
  onPress: () => void;
}) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={styles.bottomTab}>
      {icon}
      <Text style={active ? styles.bottomTabTextActive : styles.bottomTabText}>
        {label}
      </Text>
    </Pressable>
  );
}
