import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
} from 'react-native';
import {
  ArrowLeft,
  Calendar,
  CreditCard,
  TrendingUp,
  Award,
  Apple,
  MessageSquare,
  Settings as SettingsIcon,
  Shield,
  Clock,
  Dumbbell,
  CheckCircle2,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { supabase } from '../../services/supabaseClient';

interface ScreenProps {
  navigation?: any;
}

// ---------------------------------------------------------------------------
// 1. Membership Screen (Salonlarım & Üyeliklerim)
// ---------------------------------------------------------------------------
export const MembershipScreen: React.FC<ScreenProps> = ({ navigation }) => {
  const [profile, setProfile] = useState<any>(null);

  useEffect(() => {
    AuthService.getCurrentProfile().then(setProfile);
  }, []);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Salonlarım & Üyeliklerim</Text>
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <Text style={styles.cardHeader}>Aktif Salon Üyeliği</Text>
          <Text style={styles.cardTitle}>{profile?.gym_name || 'CoachUP Performans Kulübü'}</Text>
          <View style={styles.badgeRow}>
            <View style={styles.activeBadge}>
              <Text style={styles.activeBadgeText}>Aktif Üyelik</Text>
            </View>
          </View>
          <Text style={styles.cardDetail}>Üyelik Tipi: Yıllık Premium Sporcu</Text>
          <Text style={styles.cardDetail}>Bitiş Tarihi: 31.12.2026</Text>
        </View>
      </ScrollView>
    </View>
  );
};

// ---------------------------------------------------------------------------
// 2. Training History Screen (Geçmiş & İstatistikler)
// ---------------------------------------------------------------------------
export const TrainingHistoryScreen: React.FC<ScreenProps> = ({ navigation }) => {
  const [activities, setActivities] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const { data } = await supabase
        .from('user_activities')
        .select('*')
        .eq('user_id', user.id)
        .order('activity_date', { ascending: false });
      setActivities(data || []);
      setLoading(false);
    };
    load();
  }, []);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Antrenman Geçmişi</Text>
      </View>
      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : activities.length === 0 ? (
        <View style={styles.emptyBox}>
          <Dumbbell size={40} color={Colors.textSecondaryDark} />
          <Text style={styles.emptyText}>Henüz tamamlanan antrenman geçmişiniz yok.</Text>
        </View>
      ) : (
        <FlatList
          data={activities}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.content}
          renderItem={({ item }) => (
            <View style={styles.rowCard}>
              <View style={styles.iconCircle}>
                <CheckCircle2 size={22} color="#4CAF50" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.rowTitle}>{item.activity_type || 'Antrenman'}</Text>
                <Text style={styles.rowSub}>
                  {item.duration || 45} dk · {item.calories_burned || 320} kcal
                </Text>
              </View>
              <Text style={styles.dateText}>{item.activity_date}</Text>
            </View>
          )}
        />
      )}
    </View>
  );
};

// ---------------------------------------------------------------------------
// 3. Goals Screen (Hedeflerim)
// ---------------------------------------------------------------------------
export const GoalsScreen: React.FC<ScreenProps> = ({ navigation }) => {
  const [goals, setGoals] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const { data } = await supabase
        .from('user_goals')
        .select('*')
        .eq('user_id', user.id)
        .order('created_at', { ascending: false });
      setGoals(data || []);
      setLoading(false);
    };
    load();
  }, []);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Hedeflerim</Text>
      </View>
      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={styles.content}>
          {goals.map((g) => (
            <View key={g.id} style={styles.card}>
              <Text style={styles.cardTitle}>{g.title}</Text>
              <Text style={styles.cardDetail}>İlerleme: %{g.progress_percentage || 0}</Text>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
};

// ---------------------------------------------------------------------------
// 4. Personal Records Screen (Kişisel Rekorlar & Denemeler)
// ---------------------------------------------------------------------------
export const PersonalRecordsScreen: React.FC<ScreenProps> = ({ navigation }) => {
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Kişisel Rekorlar (PR)</Text>
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.rowCard}>
          <Award size={24} color="#FF9800" />
          <View style={{ flex: 1, marginLeft: 12 }}>
            <Text style={styles.rowTitle}>Bench Press PR</Text>
            <Text style={styles.rowSub}>110 kg x 1 tekrar</Text>
          </View>
        </View>
        <View style={styles.rowCard}>
          <Award size={24} color="#FF9800" />
          <View style={{ flex: 1, marginLeft: 12 }}>
            <Text style={styles.rowTitle}>Squat PR</Text>
            <Text style={styles.rowSub}>150 kg x 3 tekrar</Text>
          </View>
        </View>
        <View style={styles.rowCard}>
          <Award size={24} color="#FF9800" />
          <View style={{ flex: 1, marginLeft: 12 }}>
            <Text style={styles.rowTitle}>Deadlift PR</Text>
            <Text style={styles.rowSub}>180 kg x 1 tekrar</Text>
          </View>
        </View>
      </ScrollView>
    </View>
  );
};

// ---------------------------------------------------------------------------
// 5. Generic Menu Sub Screen Placeholder / Wrapper
// ---------------------------------------------------------------------------
export const GenericMenuScreen: React.FC<{ title: string; navigation?: any }> = ({
  title,
  navigation,
}) => {
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{title}</Text>
      </View>
      <View style={styles.emptyBox}>
        <Text style={styles.emptyText}>{title} bilgileriniz bu ekranda yer alacaktır.</Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 16,
    backgroundColor: Colors.cardDark,
  },
  backBtn: {
    marginRight: 14,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  content: {
    padding: 20,
    gap: 14,
  },
  card: {
    backgroundColor: Colors.cardDark,
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  cardHeader: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    textTransform: 'uppercase',
    fontWeight: '700',
    marginBottom: 4,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    marginBottom: 10,
  },
  badgeRow: {
    marginBottom: 12,
  },
  activeBadge: {
    backgroundColor: 'rgba(76, 175, 80, 0.15)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    alignSelf: 'flex-start',
  },
  activeBadgeText: {
    color: '#4CAF50',
    fontSize: 12,
    fontWeight: '700',
  },
  cardDetail: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  rowCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    marginBottom: 12,
  },
  iconCircle: {
    marginRight: 12,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  rowSub: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  dateText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  emptyBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  emptyText: {
    color: Colors.textSecondaryDark,
    fontSize: 15,
    textAlign: 'center',
    marginTop: 12,
  },
});
