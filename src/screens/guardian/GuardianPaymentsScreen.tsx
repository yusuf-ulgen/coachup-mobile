import React from 'react';
import { View, Text, StyleSheet, SafeAreaView } from 'react-native';
import { Colors } from '../../theme/colors';

export const GuardianPaymentsScreen = () => {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Ödemeler & Taksitler</Text>
        <View style={styles.card}>
          <Text style={styles.cardTitle}>İşlem Geçmişi</Text>
          <Text style={styles.cardText}>Bekleyen ödeme bulunmuyor.</Text>
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
