import React from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity } from 'react-native';
import { Colors } from '../../theme/colors';

const SAMPLE_POSTS = [
  {
    id: '1',
    author: 'Mehmet Öz',
    time: '2 saat önce',
    content: 'Bugün Leg Day tamamlandı! Squat 140kg x 5 set yeni PR 🎉',
    likes: 18,
    comments: 4,
  },
  {
    id: '2',
    author: 'Zeynep Ak',
    time: '5 saat önce',
    content: 'Sabah HIIT antrenmanından sonra yeşil smoothie keyfi 🥤💪',
    likes: 24,
    comments: 7,
  },
];

export const CommunityScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.headerTitle}>Topluluk & Akış</Text>

      <FlatList
        data={SAMPLE_POSTS}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <View style={styles.postCard}>
            <View style={styles.postHeader}>
              <View style={styles.avatar}>
                <Text style={styles.avatarText}>{item.author[0]}</Text>
              </View>
              <View>
                <Text style={styles.authorName}>{item.author}</Text>
                <Text style={styles.timeText}>{item.time}</Text>
              </View>
            </View>

            <Text style={styles.postContent}>{item.content}</Text>

            <View style={styles.postFooter}>
              <TouchableOpacity style={styles.actionBtn}>
                <Text style={styles.actionText}>❤️ {item.likes} Beğeni</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.actionBtn}>
                <Text style={styles.actionText}>💬 {item.comments} Yorum</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
    paddingTop: 60,
    paddingHorizontal: 20,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: Colors.textDark,
    marginBottom: 20,
  },
  postCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 18,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  postHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  avatar: {
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: Colors.purpleSecondary,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  avatarText: {
    color: Colors.allWhite,
    fontWeight: '700',
  },
  authorName: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textDark,
  },
  timeText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  postContent: {
    fontSize: 15,
    color: Colors.textDark,
    lineHeight: 22,
    marginBottom: 16,
  },
  postFooter: {
    flexDirection: 'row',
    gap: 20,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
    paddingTop: 12,
  },
  actionBtn: {},
  actionText: {
    color: Colors.textSecondaryDark,
    fontSize: 13,
    fontWeight: '600',
  },
});
