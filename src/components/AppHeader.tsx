import React from 'react';
import {Pressable, Text, TextInput, View} from 'react-native';

import {styles} from '../styles/appStyles';
import {CloseIcon, SearchIcon} from './Icons';

export function AppHeader({
  title,
  searchVisible = false,
  searchQuery = '',
  onToggleSearch,
  onChangeSearch,
}: {
  title: string;
  searchVisible?: boolean;
  searchQuery?: string;
  onToggleSearch?: () => void;
  onChangeSearch?: (value: string) => void;
}) {
  return (
    <View style={styles.header}>
      <View style={styles.appBar}>
        <Text style={styles.appBarTitle}>{title}</Text>
        {onToggleSearch ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={searchVisible ? 'Close search' : 'Open search'}
            onPress={onToggleSearch}
            style={styles.appBarIconButton}>
            {searchVisible ? <CloseIcon /> : <SearchIcon compact />}
          </Pressable>
        ) : null}
      </View>
      {searchVisible && onChangeSearch ? (
        <View style={styles.appBarSearchWrap}>
          <View style={styles.appBarSearchField}>
            <SearchIcon compact />
            <TextInput
              value={searchQuery}
              onChangeText={onChangeSearch}
              placeholder="Search messages"
              placeholderTextColor="#6f7377"
              style={styles.appBarSearchInput}
              autoFocus
            />
          </View>
        </View>
      ) : null}
    </View>
  );
}
