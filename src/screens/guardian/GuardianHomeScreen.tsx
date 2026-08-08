import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';

export const GuardianHomeScreen = () => {
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Veli Ana Ekran</Text>
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Çocukların Günlük Özeti</Text>
          <Text style={styles.cardText}>Henüz bir aktivite bulunmuyor.</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  content: {
    padding: 16,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 20,
  },
  card: {
    backgroundColor: Colors.cardDark,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: Colors.allWhite,
    marginBottom: 8,
  },
  cardText: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
  },
});
