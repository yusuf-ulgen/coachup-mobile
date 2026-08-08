import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '../../theme/colors';

export const GuardianChildDetailScreen = () => {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Çocuk Detayı</Text>
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Aktif Üyelikler & Ders Katılımları</Text>
          <Text style={styles.cardText}>Detay bulunamadı.</Text>
        </View>
      </View>
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
