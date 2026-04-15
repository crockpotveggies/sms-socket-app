import React from 'react';
import {Text, TextInput, TextInputProps, View} from 'react-native';

import {styles} from '../styles/appStyles';

export function LabeledInput({
  label,
  ...props
}: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  placeholder: string;
  keyboardType?: 'default' | 'numeric';
  secureTextEntry?: boolean;
  editable?: boolean;
} & TextInputProps) {
  return (
    <View style={styles.inputGroup}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        placeholderTextColor="#6f8194"
        style={[styles.input, props.editable === false ? styles.inputDisabled : null]}
        autoCapitalize="none"
        autoCorrect={false}
        {...props}
      />
    </View>
  );
}
