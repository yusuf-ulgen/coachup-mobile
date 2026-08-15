import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  TextInput,
  ActivityIndicator,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { feedback } from '../../services/feedbackService';
import {
  BarChart2,
  Search,
  Flame,
  ChevronRight,
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
import { isOutdoorWorkout } from '../../services/locationService';
import { Collapsible } from '../../components/motion/Collapsible';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface TrainingScreenProps {
  navigation?: any;
}

const BUILTIN_ACTIVITIES = [
  { id: 'fitness', title: 'Fitness', emoji: '🏋️', hint: 'Süre · Nabız', color: '#FF5722' },
  { id: 'running', title: 'Koşu', emoji: '🏃', hint: 'Süre · Mesafe · Tempo', color: '#00E5FF' },
  { id: 'walking', title: 'Yürüyüş', emoji: '🚶', hint: 'Süre · Mesafe · Tempo', color: '#00E676' },
  { id: 'cycling', title: 'Bisiklet', emoji: '🚴', hint: 'Süre · Mesafe · Hız', color: '#FFD600' },
  { id: 'swimming', title: 'Yüzme', emoji: '🏊', hint: 'Süre · Mesafe', color: '#2979FF' },
  { id: 'combat', title: 'Dövüş Sporları', emoji: '🥊', hint: 'Süre · Nabız', color: '#FF1744' },
  { id: 'yoga', title: 'Yoga', emoji: '🧘', hint: 'Süre · Nabız', color: '#E040FB' },
  { id: 'pilates', title: 'Pilates', emoji: '🤸', hint: 'Süre · Nabız', color: '#FF4081' },
  { id: 'crossfit', title: 'CrossFit', emoji: '🔥', hint: 'Süre · Nabız', color: '#FF6D00' },
  { id: 'functional', title: 'Functional Fitness', emoji: '⚡', hint: 'Süre · Nabız', color: '#FFB300' },
  { id: 'hyrox', title: 'Hyrox', emoji: '🏁', hint: 'Süre · Mesafe · Tempo', color: '#76FF03' },
  { id: 'custom', title: 'Özel Aktivite', emoji: '📋', hint: 'Serbest kayıt', color: '#64B5F6' },
];

export const TrainingScreen: React.FC<TrainingScreenProps> = ({ navigation }) => {
  const insets = useSafeAreaInsets();
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
      ActiveWorkoutManager.startWorkout(session.id, prog.name, prog.id, 0, {
        category: prog.category || 'Salon',
        selectedDay: selectedDay,
      });
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
    const isAi = prog.source === 'ai';
    const accentColor = isAi ? '#AB47BC' : Colors.primary;
    const btnGradient: [string, string] = isAi ? ['#BA68C8', '#7B1FA2'] : ['#FF6B4A', '#FF3D00'];
    const difficultyMap: Record<string, { label: string; color: string; bg: string }> = {
      beginner: { label: 'Başlangıç', color: '#4CAF50', bg: 'rgba(76, 175, 80, 0.15)' },
      intermediate: { label: 'Orta', color: '#FF9800', bg: 'rgba(255, 152, 0, 0.15)' },
      advanced: { label: 'İleri', color: '#F44336', bg: 'rgba(244, 67, 54, 0.15)' },
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
          <View style={{ flex: 1, paddingRight: 8 }}>
            <Text style={styles.progTitle} numberOfLines={2}>{prog.name}</Text>
            <View style={styles.badgeRow}>
              <View
                style={[
                  styles.badge,
                  { backgroundColor: `${accentColor}20`, borderColor: `${accentColor}40` },
                ]}
              >
                <Text style={[styles.badgeText, { color: accentColor }]}>
                  {isAi ? 'AI Program' : 'Salon'}
                </Text>
              </View>
              {diffInfo && (
                <View style={[styles.diffBadge, { backgroundColor: diffInfo.bg }]}>
                  <View style={[styles.diffDot, { backgroundColor: diffInfo.color }]} />
                  <Text style={{ color: diffInfo.color, fontSize: 11, fontWeight: '700' }}>
                    {diffInfo.label}
                  </Text>
                </View>
              )}
              {prog.category && (
                <Text style={styles.categoryText}>{prog.category}</Text>
              )}
            </View>
          </View>
          <TouchableOpacity
            style={styles.startButtonTouch}
            activeOpacity={0.85}
            disabled={isStarting}
            onPress={() => {
              setPreviewProgram(prog);
              setShowPreviewModal(true);
            }}
          >
            <LinearGradient
              colors={btnGradient}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={styles.startButtonGradient}
            >
              {isStarting ? (
                <ActivityIndicator size="small" color={Colors.allWhite} />
              ) : (
                <Text style={styles.startButtonText}>Programa Başla</Text>
              )}
            </LinearGradient>
          </TouchableOpacity>
        </View>

        {prog.description ? (
          <Text style={styles.progDescription}>{prog.description}</Text>
        ) : null}

        <TouchableOpacity
          style={styles.expandRow}
          onPress={() => setExpandedProgramId(isExpanded ? null : prog.id)}
          activeOpacity={0.75}
        >
          <Text style={styles.expandText}>Program Detayı</Text>
          {isExpanded ? (
            <ChevronUp size={16} color={Colors.textSecondaryDark} />
          ) : (
            <ChevronDown size={16} color={Colors.textSecondaryDark} />
          )}
        </TouchableOpacity>

        <Collapsible expanded={isExpanded}>
          <View style={styles.expandedContent}>
            {prog.program_text ? (
              <Text style={{ fontSize: 13, color: Colors.textDark, lineHeight: 19 }}>
                {prog.program_text}
              </Text>
            ) : prog.exercise_names && prog.exercise_names.length > 0 ? (
              <View style={{ gap: 6 }}>
                {prog.exercise_names.slice(0, 12).map((exName, i) => (
                  <View key={i} style={styles.exerciseRowItem}>
                    <View style={[styles.exerciseIndexBadge, { backgroundColor: `${accentColor}18` }]}>
                      <Text style={[styles.exerciseIndexText, { color: accentColor }]}>{i + 1}</Text>
                    </View>
                    <Text style={styles.exerciseNameText}>{exName}</Text>
                  </View>
                ))}
                {prog.exercise_names.length > 12 && (
                  <Text style={styles.moreExercisesText}>
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
      <Header navigation={navigation} showMenuButton={true} onMenuPress={() => setMenuVisible(true)} />
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

      <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 100 + insets.bottom }]} showsVerticalScrollIndicator={false}>
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
          style={styles.rekorTouch}
          onPress={() => navigation?.navigate('RecordAttemptSetup')}
          activeOpacity={0.82}
        >
          <LinearGradient
            colors={['#2A1814', '#1A1214']}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={styles.rekorGradient}
          >
            <View style={styles.rekorLeft}>
              <LinearGradient
                colors={['#FF6B4A', '#FF3D00']}
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 1 }}
                style={styles.rekorIconBox}
              >
                <Flame size={20} color={Colors.allWhite} />
              </LinearGradient>
              <View style={{ flex: 1 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  <Text style={styles.rekorCardTitle}>Rekor Denemesi</Text>
                  <View style={styles.rekorBadge}>
                    <Text style={styles.rekorBadgeText}>YENİ</Text>
                  </View>
                </View>
                <Text style={styles.rekorCardSub}>Kişisel en iyilerini test et ve kaydet</Text>
              </View>
            </View>
            <View style={styles.rekorArrowBox}>
              <ChevronRight size={18} color="#FF6B4A" />
            </View>
          </LinearGradient>
        </TouchableOpacity>

        {/* Aktiviteler Grid */}
        <View style={styles.sectionHeaderRow}>
          <LinearGradient
            colors={['#FF6B4A', '#FF3D00']}
            start={{ x: 0, y: 0 }}
            end={{ x: 0, y: 1 }}
            style={styles.sectionAccentLine}
          />
          <Text style={styles.sectionTitle}>Aktiviteler</Text>
        </View>
        <Text style={styles.sectionSubtitle}>Saatinden veya manuel olarak kaydet</Text>

        <View style={styles.activityGrid}>
          {BUILTIN_ACTIVITIES.map((act) => (
            <TouchableOpacity
              key={act.id}
              style={styles.activityCardTouch}
              activeOpacity={0.78}
              onPress={() => {
                setSelectedActivity(act);
                const isActOutdoor = isOutdoorWorkout(act.id, act.title);

                if (isActOutdoor) {
                  setShowGoalSheet(true);
                } else {
                  setShowPreWorkoutModal(true);
                }
              }}
            >
              <LinearGradient
                colors={['#202026', '#161619']}
                start={{ x: 0, y: 0 }}
                end={{ x: 0.8, y: 1 }}
                style={styles.activityCardGradient}
              >
                <View style={[styles.activityEmojiBox, { backgroundColor: `${act.color}18`, borderColor: `${act.color}35` }]}>
                  <Text style={styles.activityEmoji}>{act.emoji}</Text>
                </View>
                <Text style={styles.activityTitle}>{act.title}</Text>
                <View style={styles.activityHintPill}>
                  <View style={[styles.activityHintDot, { backgroundColor: act.color }]} />
                  <Text style={styles.activityHint} numberOfLines={1}>{act.hint}</Text>
                </View>
              </LinearGradient>
            </TouchableOpacity>
          ))}
        </View>

        {/* Kişiye Özel Programlar Section */}
        {personalizedPrograms.length > 0 && (
          <View style={{ marginTop: 28 }}>
            <View style={styles.sectionHeaderRow}>
              <LinearGradient
                colors={['#FF6B4A', '#FF3D00']}
                start={{ x: 0, y: 0 }}
                end={{ x: 0, y: 1 }}
                style={styles.sectionAccentLine}
              />
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
        <View style={[styles.sectionHeaderRow, { marginTop: 28 }]}>
          <LinearGradient
            colors={['#FF6B4A', '#FF3D00']}
            start={{ x: 0, y: 0 }}
            end={{ x: 0, y: 1 }}
            style={styles.sectionAccentLine}
          />
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
          <View style={{ marginTop: 28 }}>
            <View style={styles.sectionHeaderRow}>
              <LinearGradient
                colors={['#BA68C8', '#7B1FA2']}
                start={{ x: 0, y: 0 }}
                end={{ x: 0, y: 1 }}
                style={styles.sectionAccentLine}
              />
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

      {/* Program Preview Modal */}
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
            ? isOutdoorWorkout(selectedActivity.id, selectedActivity.title)
            : false
        }
        onClose={() => setShowGoalSheet(false)}
        onSelectGoal={(goal: WorkoutGoal) => {
          if (selectedActivity) {
            const isActOutdoor = isOutdoorWorkout(selectedActivity.id, selectedActivity.title);
            const sessionId = `free_${Date.now()}`;
            ActiveWorkoutManager.startWorkout(sessionId, selectedActivity.title, undefined, 0, {
              workoutTitle: selectedActivity.title,
              category: selectedActivity.id,
              emoji: selectedActivity.emoji,
              isOutdoor: isActOutdoor,
              hasStarted: !isActOutdoor,
            });
            navigation?.navigate('ActiveWorkout', {
              sessionId,
              title: selectedActivity.title,
              workoutTitle: selectedActivity.title,
              category: selectedActivity.id,
              emoji: selectedActivity.emoji,
              goalLabel: goal.label,
              goalType: goal.type,
              distanceKm: goal.distanceKm,
              durationSeconds: goal.durationSeconds,
            });
          }
        }}
      />

      {/* Pre-Workout Start Modal for Indoor Free Activities */}
      <PreWorkoutStartModal
        visible={showPreWorkoutModal}
        activityTitle={selectedActivity?.title || 'Fitness'}
        activityEmoji={selectedActivity?.emoji || '🏋️'}
        onClose={() => setShowPreWorkoutModal(false)}
        onStart={() => {
          setShowPreWorkoutModal(false);
          if (selectedActivity) {
            const isActOutdoor = isOutdoorWorkout(selectedActivity.id, selectedActivity.title);
            const sessionId = `free_${Date.now()}`;
            ActiveWorkoutManager.startWorkout(sessionId, selectedActivity.title, undefined, 0, {
              workoutTitle: selectedActivity.title,
              category: selectedActivity.id,
              emoji: selectedActivity.emoji,
              isOutdoor: isActOutdoor,
              hasStarted: !isActOutdoor,
            });
            navigation?.navigate('ActiveWorkout', {
              sessionId,
              title: selectedActivity.title,
              workoutTitle: selectedActivity.title,
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
    fontWeight: '800',
    color: Colors.textDark,
    letterSpacing: -0.3,
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
    shadowColor: Colors.primary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(255, 96, 71, 0.2)',
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingBottom: 100,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#18181C',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    paddingHorizontal: 14,
    height: 48,
    marginBottom: 16,
    gap: 10,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
  },
  searchInput: {
    flex: 1,
    color: Colors.textDark,
    fontSize: 15,
  },
  rekorTouch: {
    borderRadius: 18,
    overflow: 'hidden',
    marginBottom: 24,
    elevation: 4,
    shadowColor: Colors.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 96, 71, 0.3)',
  },
  rekorGradient: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  rekorLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    gap: 12,
  },
  rekorIconBox: {
    width: 42,
    height: 42,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    shadowColor: Colors.primary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.4,
    shadowRadius: 4,
  },
  rekorCardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textDark,
  },
  rekorBadge: {
    backgroundColor: 'rgba(255, 96, 71, 0.2)',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(255, 96, 71, 0.3)',
  },
  rekorBadgeText: {
    color: Colors.primary,
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  rekorCardSub: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  rekorArrowBox: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: 'rgba(255, 96, 71, 0.12)',
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 8,
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
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '800',
    color: Colors.textDark,
    letterSpacing: -0.2,
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
  activityCardTouch: {
    width: '48%',
    borderRadius: 18,
    overflow: 'hidden',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
  },
  activityCardGradient: {
    padding: 14,
    minHeight: 120,
    justifyContent: 'space-between',
  },
  activityEmojiBox: {
    width: 40,
    height: 40,
    borderRadius: 12,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 8,
  },
  activityEmoji: {
    fontSize: 20,
  },
  activityTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textDark,
    letterSpacing: -0.2,
  },
  activityHintPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderRadius: 8,
    paddingHorizontal: 6,
    paddingVertical: 3,
    marginTop: 6,
    gap: 5,
  },
  activityHintDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
  },
  activityHint: {
    fontSize: 10,
    color: Colors.textSecondaryDark,
    fontWeight: '500',
    flex: 1,
  },
  emptyCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 18,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  emptyText: {
    color: Colors.textSecondaryDark,
    fontSize: 14,
  },
  programList: {
    gap: 14,
  },
  programCard: {
    backgroundColor: '#18181D',
    borderRadius: 18,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.07)',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  cardHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  progTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
    letterSpacing: -0.2,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 6,
    flexWrap: 'wrap',
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
    borderWidth: 1,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  diffBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  diffDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
  },
  categoryText: {
    color: Colors.textSecondaryDark,
    fontSize: 11,
    fontWeight: '500',
  },
  startButtonTouch: {
    borderRadius: 12,
    overflow: 'hidden',
    elevation: 6,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.4,
    shadowRadius: 6,
  },
  startButtonGradient: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    justifyContent: 'center',
    alignItems: 'center',
    minWidth: 104,
  },
  startButtonText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 12,
    letterSpacing: 0.2,
  },
  progDescription: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 10,
    lineHeight: 18,
  },
  expandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginTop: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.04)',
  },
  expandText: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.textDark,
  },
  expandedContent: {
    marginTop: 10,
    paddingHorizontal: 4,
    paddingVertical: 6,
    gap: 6,
  },
  exerciseRowItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  exerciseIndexBadge: {
    width: 22,
    height: 22,
    borderRadius: 11,
    justifyContent: 'center',
    alignItems: 'center',
  },
  exerciseIndexText: {
    fontSize: 12,
    fontWeight: '700',
  },
  exerciseNameText: {
    fontSize: 13,
    color: Colors.textDark,
    fontWeight: '500',
    flex: 1,
  },
  moreExercisesText: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 4,
    fontStyle: 'italic',
  },
});
