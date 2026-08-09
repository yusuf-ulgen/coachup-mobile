import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  TextInput,
  ActivityIndicator,
  FlatList,
} from 'react-native';
import { feedback } from '../../services/feedbackService';
import {
  BarChart2,
  Search,
  Flame,
  ChevronRight,
  Dumbbell,
  ChevronDown,
  ChevronUp,
  XCircle,
} from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { AuthService } from '../../services/authService';
import { TrainingService, TrainingProgram } from '../../services/trainingService';
import { WorkoutGoalSheet, WorkoutGoal } from '../../components/WorkoutGoalSheet';
import { Header } from '../../components/Header';
import { SideMenu } from '../../components/SideMenu';
import { ProgramPreviewModal } from '../../components/ProgramPreviewModal';
import { PreWorkoutStartModal } from '../../components/PreWorkoutStartModal';
import { CustomAlert } from '../../components/CustomAlertModal';
import { supabase } from '../../services/supabaseClient';
import { ActiveWorkoutManager } from '../../services/activeWorkoutManager';
import { Collapsible } from '../../components/motion/Collapsible';

interface TrainingScreenProps {
  navigation?: any;
}

const BUILTIN_ACTIVITIES = [
  { id: 'fitness', title: 'Fitness', emoji: '🏋️', hint: 'Süre · Nabız' },
  { id: 'running', title: 'Koşu', emoji: '🏃', hint: 'Süre · Mesafe · Tempo' },
  { id: 'walking', title: 'Yürüyüş', emoji: '🚶', hint: 'Süre · Mesafe · Tempo' },
  { id: 'cycling', title: 'Bisiklet', emoji: '🚴', hint: 'Süre · Mesafe · Hız' },
  { id: 'swimming', title: 'Yüzme', emoji: '🏊', hint: 'Süre · Mesafe' },
  { id: 'combat', title: 'Dövüş Sporları', emoji: '🥊', hint: 'Süre · Nabız' },
  { id: 'yoga', title: 'Yoga', emoji: '🧘', hint: 'Süre · Nabız' },
  { id: 'pilates', title: 'Pilates', emoji: '🤸', hint: 'Süre · Nabız' },
  { id: 'crossfit', title: 'CrossFit', emoji: '🔥', hint: 'Süre · Nabız' },
  { id: 'functional', title: 'Functional Fitness', emoji: '⚡', hint: 'Süre · Nabız' },
  { id: 'hyrox', title: 'Hyrox', emoji: '🏁', hint: 'Süre · Mesafe · Tempo' },
  { id: 'custom', title: 'Özel Aktivite', emoji: '📋', hint: 'Serbest kayıt' },
];

export const TrainingScreen: React.FC<TrainingScreenProps> = ({ navigation }) => {
  const [searchText, setSearchText] = useState('');
  const [userProfile, setUserProfile] = useState<any>(null);
  const [gymPrograms, setGymPrograms] = useState<TrainingProgram[]>([]);
  const [aiPrograms, setAiPrograms] = useState<TrainingProgram[]>([]);
  const [loading, setLoading] = useState(false);
  const [expandedProgramId, setExpandedProgramId] = useState<string | null>(null);

  // Goal Sheet State
  const [selectedActivity, setSelectedActivity] = useState<any>(null);
  const [showGoalSheet, setShowGoalSheet] = useState(false);
  const [showPreWorkoutModal, setShowPreWorkoutModal] = useState(false);

  // Program Preview Modal State
  const [previewProgram, setPreviewProgram] = useState<TrainingProgram | null>(null);
  const [showPreviewModal, setShowPreviewModal] = useState(false);

  useEffect(() => {
    const loadProfileAndPrograms = async () => {
      setLoading(true);
      try {
        const profile = await AuthService.getCurrentProfile();
        setUserProfile(profile);
        const [programs, aiProgs] = await Promise.all([
          TrainingService.fetchGymPrograms(profile?.gym_id),
          TrainingService.fetchAiPrograms(),
        ]);
        setGymPrograms(programs);
        setAiPrograms(aiProgs);
      } catch (e) {
        console.error('Error loading training programs:', e);
      } finally {
        setLoading(false);
      }
    };
    loadProfileAndPrograms();
  }, []);

  const userId = userProfile?.id || userProfile?.user_id;

  const filteredGym = gymPrograms.filter((p) =>
    p.name.toLowerCase().includes(searchText.toLowerCase())
  );

  const personalizedPrograms = filteredGym.filter(
    (p) =>
      (p.privacy === 'private' || p.privacy === 'members') &&
      (userId ? p.visible_member_ids?.includes(userId) : true)
  );

  const regularGymPrograms = filteredGym.filter(
    (p) => p.privacy !== 'private' && p.privacy !== 'members'
  );

  const filteredAiPrograms = aiPrograms.filter((p) =>
    p.name.toLowerCase().includes(searchText.toLowerCase())
  );

  const [startingProgramId, setStartingProgramId] = useState<string | null>(null);

  const handleStartProgram = async (prog: TrainingProgram, selectedDay: number = 1) => {
    const uid = userProfile?.id || userProfile?.user_id;
    if (!uid) return;

    setStartingProgramId(prog.id);
    try {
      // Check active in_progress session
      const { data: activeData } = await supabase
        .from('training_sessions')
        .select('*')
        .eq('user_id', uid)
        .eq('status', 'in_progress')
        .maybeSingle();

      if (activeData) {
        setShowPreviewModal(false);
        CustomAlert.show({
          title: 'Aktif Antrenman Mevcut',
          message: 'Zaten devam eden bir antrenmanınız var. Lütfen önce mevcut antrenmanı bitirin veya antrenmana dönün.',
          type: 'warning',
          buttons: [
            { text: 'Vazgeç', style: 'cancel' },
            {
              text: 'Antrenmana Dön',
              onPress: () =>
                navigation?.navigate('ActiveWorkout', {
                  sessionId: activeData.id,
                  programId: activeData.program_id,
                  title: prog.name,
                }),
            },
          ],
        });
        return;
      }

      // Create new session via TrainingService
      const session = await TrainingService.startSession(uid, prog.id, prog.gym_id);
      setShowPreviewModal(false);
      ActiveWorkoutManager.startWorkout(session.id, prog.name, prog.id);
      navigation?.navigate('ActiveWorkout', {
        sessionId: session.id,
        programId: prog.id,
        title: prog.name,
        category: prog.category || 'Salon',
        selectedDay: selectedDay,
      });
    } catch (e: any) {
      console.error('Start program error:', e);
      feedback.error({
        title: 'Hata',
        message: e,
        fallbackMessage: 'Antrenman başlatılamadı.',
      });
    } finally {
      setStartingProgramId(null);
    }
  };

  const renderProgramCard = (prog: TrainingProgram) => {
    const isExpanded = expandedProgramId === prog.id;
    const isStarting = startingProgramId === prog.id;
    const accentColor = prog.source === 'ai' ? '#7B1FA2' : Colors.primary;
    const difficultyMap: Record<string, { label: string; color: string }> = {
      beginner: { label: 'Başlangıç', color: '#4CAF50' },
      intermediate: { label: 'Orta', color: '#FF9800' },
      advanced: { label: 'İleri', color: '#F44336' },
    };
    const diffInfo = prog.difficulty ? difficultyMap[prog.difficulty] : null;

    return (
      <View
        key={prog.id}
        style={[
          styles.programCard,
          { borderLeftWidth: 4, borderLeftColor: accentColor },
        ]}
      >
        <View style={styles.cardHeaderRow}>
          <View style={{ flex: 1 }}>
            <Text style={styles.progTitle}>{prog.name}</Text>
            <View style={styles.badgeRow}>
              <View
                style={[
                  styles.badge,
                  { backgroundColor: `${accentColor}26` },
                ]}
              >
                <Text style={[styles.badgeText, { color: accentColor }]}>
                  {prog.source === 'ai' ? 'AI Program' : 'Salon'}
                </Text>
              </View>
              {diffInfo && (
                <Text style={{ color: diffInfo.color, fontSize: 12, fontWeight: '600' }}>
                  {diffInfo.label}
                </Text>
              )}
              {prog.category && (
                <Text style={styles.categoryText}>{prog.category}</Text>
              )}
            </View>
          </View>
          <TouchableOpacity
            style={styles.startButton}
            activeOpacity={0.8}
            disabled={isStarting}
            onPress={() => {
              setPreviewProgram(prog);
              setShowPreviewModal(true);
            }}
          >
            {isStarting ? (
              <ActivityIndicator size="small" color={Colors.allWhite} />
            ) : (
              <Text style={styles.startButtonText}>Programe Başlat</Text>
            )}
          </TouchableOpacity>
        </View>

        {prog.description ? (
          <Text style={styles.progDescription}>{prog.description}</Text>
        ) : null}

        <TouchableOpacity
          style={styles.expandRow}
          onPress={() => setExpandedProgramId(isExpanded ? null : prog.id)}
          activeOpacity={0.7}
        >
          <Text style={styles.expandText}>Program Detayı</Text>
          {isExpanded ? (
            <ChevronUp size={18} color={Colors.textSecondaryDark} />
          ) : (
            <ChevronDown size={18} color={Colors.textSecondaryDark} />
          )}
        </TouchableOpacity>

        <Collapsible expanded={isExpanded}>
          <View style={{ marginTop: 10, paddingHorizontal: 4, gap: 6 }}>
            {prog.program_text ? (
              <Text style={{ fontSize: 13, color: Colors.textDark }}>
                {prog.program_text}
              </Text>
            ) : prog.exercise_names && prog.exercise_names.length > 0 ? (
              <View style={{ gap: 4 }}>
                {prog.exercise_names.slice(0, 12).map((exName, i) => (
                  <Text key={i} style={{ fontSize: 13, color: Colors.textDark }}>
                    <Text style={{ fontWeight: '700', color: accentColor }}>
                      {i + 1}.{' '}
                    </Text>
                    {exName}
                  </Text>
                ))}
                {prog.exercise_names.length > 12 && (
                  <Text style={{ fontSize: 12, color: Colors.textSecondaryDark, marginTop: 4 }}>
                    +{prog.exercise_names.length - 12} hareket daha
                  </Text>
                )}
              </View>
            ) : (
              <Text style={{ fontSize: 12, color: Colors.textSecondaryDark }}>
                Egzersiz detayları bulunmuyor
              </Text>
            )}
          </View>
        </Collapsible>
      </View>
    );
  };

  const [menuVisible, setMenuVisible] = useState(false);

  return (
    <View style={styles.container}>
      <Header navigation={navigation} showMenuButton={false} />
      <SideMenu visible={menuVisible} onClose={() => setMenuVisible(false)} navigation={navigation} />

      {/* Screen Sub-header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>Antrenman</Text>
          <Text style={styles.headerSubtitle}>Aktivite kaydet veya programını başlat</Text>
        </View>
        <TouchableOpacity
          style={styles.statsButton}
          onPress={() => navigation?.navigate('PersonalRecords')}
          activeOpacity={0.8}
        >
          <BarChart2 size={20} color={Colors.primary} />
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Search Bar */}
        <View style={styles.searchBar}>
          <Search size={18} color={Colors.textSecondaryDark} />
          <TextInput
            style={styles.searchInput}
            placeholder="Program ara..."
            placeholderTextColor={Colors.textSecondaryDark}
            value={searchText}
            onChangeText={setSearchText}
          />
          {searchText.length > 0 && (
            <TouchableOpacity onPress={() => setSearchText('')} activeOpacity={0.7}>
              <XCircle size={18} color={Colors.textSecondaryDark} />
            </TouchableOpacity>
          )}
        </View>

        {/* Rekor Denemesi Card Button */}
        <TouchableOpacity
          style={styles.rekorCard}
          onPress={() => navigation?.navigate('RecordAttemptSetup')}
          activeOpacity={0.8}
        >
          <Flame size={20} color={Colors.primary} />
          <Text style={styles.rekorCardText}>Rekor Denemesi</Text>
          <ChevronRight size={18} color={Colors.textSecondaryDark} />
        </TouchableOpacity>

        {/* Aktiviteler Grid */}
        <View style={styles.sectionHeaderRow}>
          <View style={styles.sectionAccentLine} />
          <Text style={styles.sectionTitle}>Aktiviteler</Text>
        </View>
        <Text style={styles.sectionSubtitle}>Saatinden veya manuel olarak kaydet</Text>

        <View style={styles.activityGrid}>
          {BUILTIN_ACTIVITIES.map((act) => (
            <TouchableOpacity
              key={act.id}
              style={styles.activityCard}
              activeOpacity={0.8}
              onPress={() => {
                setSelectedActivity(act);
                const isActOutdoor = ['running', 'walking', 'cycling', 'hyrox', 'swimming', 'koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].includes(
                  (act.id || '').toLowerCase()
                ) || ['koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].some((kw) => (act.title || '').toLowerCase().includes(kw));

                if (isActOutdoor) {
                  setShowGoalSheet(true);
                } else {
                  setShowPreWorkoutModal(true);
                }
              }}
            >
              <Text style={styles.activityEmoji}>{act.emoji}</Text>
              <Text style={styles.activityTitle}>{act.title}</Text>
              <Text style={styles.activityHint}>{act.hint}</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Kişiye Özel Programlar Section */}
        {personalizedPrograms.length > 0 && (
          <View style={{ marginTop: 24 }}>
            <View style={styles.sectionHeaderRow}>
              <View style={styles.sectionAccentLine} />
              <Text style={styles.sectionTitle}>Kişiye Özel Programlar</Text>
            </View>
            <Text style={styles.sectionSubtitle}>
              Kişisel antrenörünüzün size özel hazırladığı programlar
            </Text>
            <View style={styles.programList}>
              {personalizedPrograms.map(renderProgramCard)}
            </View>
          </View>
        )}

        {/* Salon Antrenmanları Section */}
        <View style={[styles.sectionHeaderRow, { marginTop: 24 }]}>
          <View style={styles.sectionAccentLine} />
          <Text style={styles.sectionTitle}>Salon Antrenmanları</Text>
        </View>
        <Text style={styles.sectionSubtitle}>
          Salonun kendi antrenmanları (CrossFit WOD, HIIT vb.)
        </Text>

        {loading ? (
          <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 20 }} />
        ) : regularGymPrograms.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyText}>Salon antrenmanı bulunamadı</Text>
          </View>
        ) : (
          <View style={styles.programList}>
            {regularGymPrograms.map(renderProgramCard)}
          </View>
        )}

        {/* AI Programları Section */}
        {filteredAiPrograms.length > 0 && (
          <View style={{ marginTop: 24 }}>
            <View style={styles.sectionHeaderRow}>
              <View style={[styles.sectionAccentLine, { backgroundColor: '#7B1FA2' }]} />
              <Text style={styles.sectionTitle}>AI Programları</Text>
            </View>
            <Text style={styles.sectionSubtitle}>
              Kişisel yapay zeka programların
            </Text>
            <View style={styles.programList}>
              {filteredAiPrograms.map(renderProgramCard)}
            </View>
          </View>
        )}
      </ScrollView>

      {/* Program Preview Modal (Image 1) */}
      <ProgramPreviewModal
        visible={showPreviewModal}
        program={previewProgram}
        onClose={() => setShowPreviewModal(false)}
        onStartWorkout={(day) => {
          if (previewProgram) {
            handleStartProgram(previewProgram, day);
          }
        }}
        isStarting={!!startingProgramId}
      />

      {/* Workout Goal Selection Sheet */}
      <WorkoutGoalSheet
        visible={showGoalSheet}
        isOutdoor={
          selectedActivity
            ? ['running', 'walking', 'cycling', 'hyrox', 'swimming', 'koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].includes(
                (selectedActivity.id || '').toLowerCase()
              ) ||
              ['koşu', 'yürüyüş', 'bisiklet', 'hyrox', 'yüzme'].some((kw) =>
                (selectedActivity.title || '').toLowerCase().includes(kw)
              )
            : false
        }
        onClose={() => setShowGoalSheet(false)}
        onSelectGoal={(goal: WorkoutGoal) => {
          if (selectedActivity) {
            const sessionId = `free_${Date.now()}`;
            ActiveWorkoutManager.startWorkout(sessionId, selectedActivity.title);
            navigation?.navigate('ActiveWorkout', {
              sessionId,
              title: selectedActivity.title,
              category: selectedActivity.id,
              goalLabel: goal.label,
              goalType: goal.type,
              distanceKm: goal.distanceKm,
              durationSeconds: goal.durationSeconds,
            });
          }
        }}
      />

      {/* Pre-Workout Start Modal for Indoor Free Activities (Image 1) */}
      <PreWorkoutStartModal
        visible={showPreWorkoutModal}
        activityTitle={selectedActivity?.title || 'Fitness'}
        activityEmoji={selectedActivity?.emoji || '🏋️'}
        onClose={() => setShowPreWorkoutModal(false)}
        onStart={() => {
          setShowPreWorkoutModal(false);
          if (selectedActivity) {
            const sessionId = `free_${Date.now()}`;
            ActiveWorkoutManager.startWorkout(sessionId, selectedActivity.title);
            navigation?.navigate('ActiveWorkout', {
              sessionId,
              title: selectedActivity.title,
              category: selectedActivity.id,
              emoji: selectedActivity.emoji,
            });
          }
        }}
      />
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
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 16,
    backgroundColor: Colors.backgroundDark,
  },
  headerTitle: {
    fontSize: 26,
    fontWeight: '700',
    color: Colors.textDark,
  },
  headerSubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  statsButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.cardDark,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 100,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    paddingHorizontal: 14,
    height: 46,
    marginBottom: 16,
    gap: 10,
  },
  searchInput: {
    flex: 1,
    color: Colors.textDark,
    fontSize: 15,
  },
  rekorCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderWidth: 1,
    borderColor: 'rgba(255, 96, 71, 0.25)',
    marginBottom: 20,
  },
  rekorCardText: {
    flex: 1,
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
    marginLeft: 10,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  sectionAccentLine: {
    width: 4,
    height: 18,
    borderRadius: 2,
    backgroundColor: Colors.primary,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: Colors.textDark,
  },
  sectionSubtitle: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
    marginBottom: 14,
  },
  activityGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  activityCard: {
    width: '48%',
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  activityEmoji: {
    fontSize: 28,
    marginBottom: 8,
  },
  activityTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  activityHint: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
  emptyCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
  },
  emptyText: {
    color: Colors.textSecondaryDark,
    fontSize: 14,
  },
  programList: {
    gap: 14,
  },
  programCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  cardHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  progTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textDark,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 4,
  },
  badge: {
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  badgeText: {
    color: Colors.primary,
    fontSize: 11,
    fontWeight: '600',
  },
  categoryText: {
    color: Colors.textSecondaryDark,
    fontSize: 12,
  },
  startButton: {
    backgroundColor: Colors.primary,
    borderRadius: 10,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  startButtonText: {
    color: Colors.allWhite,
    fontWeight: '600',
    fontSize: 13,
  },
  progDescription: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 10,
  },
  expandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginTop: 12,
  },
  expandText: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.textDark,
  },
});
