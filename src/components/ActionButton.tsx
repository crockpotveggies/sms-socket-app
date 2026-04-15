import React from 'react';
import {Pressable, Text} from 'react-native';

import {styles} from '../styles/appStyles';

export function ActionButton({
  label,
  onPress,
  tone = 'primary',
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  tone?: 'primary' | 'secondary';
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.button,
        tone === 'secondary' ? styles.buttonSecondary : styles.buttonPrimary,
        disabled ? styles.buttonDisabled : null,
      ]}>
      <Text
        style={
          tone === 'secondary'
            ? [styles.buttonTextSecondary, disabled ? styles.buttonTextDisabled : null]
            : [styles.buttonTextPrimary, disabled ? styles.buttonTextDisabled : null]
        }>
        {label}
      </Text>
    </Pressable>
  );
}
