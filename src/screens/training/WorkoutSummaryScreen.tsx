import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import {
  Timer,
  Flame,
  Heart,
  Activity,
  MapPin,
  TrendingUp,
  Share2,
  CheckCircle,
  Navigation,
} from 'lucide-react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Colors } from '../../theme/colors';
import { WorkoutShareSheet } from './WorkoutShareSheet';

export const WorkoutSummaryScreen: React.FC = () => {
  const navigation = useNavigation<any>();
  const route = useRoute<any>();
  
  const {
    training = { title: 'Sabah Koşusu', category: { emoji: '🏃' } },
    durationSeconds = 1800,
    calories = 320,
    avgHeartRate = 145,
    maxHeartRate = 175,
    distanceKm = 5.2,
    avgPaceMinPerKm = 5.5,
    splits = [
      { km: 1, pace: 5.2 },
      { km: 2, pace: 5.4 },
      { km: 3, pace: 5.5 },
      { km: 4, pace: 5.6 },
      { km: 5, pace: 5.3 },
    ],
    perceivedEffort = 'Zor',
    gpsRoute = [],
  } = route.params || {};

  const [showShareSheet, setShowShareSheet] = useState(false);

  const formatDuration = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const StatCard = ({ icon: Icon, iconColor, value, label }: any) => (
    <View style={styles.statCard}>
      <View style={[styles.statIconBox, { backgroundColor: `${iconColor}20` }]}>
        <Icon size={18} color={iconColor} />
      </View>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Antrenman Özeti</Text>
          <View style={styles.headerRow}>
            <View style={styles.emojiBox}>
              <Text style={{ fontSize: 24 }}>{training.category.emoji}</Text>
            </View>
            <View style={styles.headerTextCol}>
              <Text style={styles.trainingTitle}>{training.title}</Text>
              <Text style={styles.trainingDate}>{new Date().toLocaleDateString()}</Text>
            </View>
            <View style={styles.checkBox}>
              <CheckCircle size={22} color="#4CAF50" />
            </View>
          </View>
        </View>

        {/* Süre / Kalori */}
        <Text style={styles.sectionLabel}>ÖZET</Text>
        <View style={styles.row}>
          <StatCard icon={Timer} iconColor={Colors.primary} value={formatDuration(durationSeconds)} label="Süre" />
          <StatCard icon={Flame} iconColor="#FF7043" value={calories.toString()} label="Kalori" />
        </View>

        {/* Nabız */}
        {avgHeartRate && (
          <>
            <View style={[styles.row, { marginTop: 12 }]}>
              <StatCard icon={Heart} iconColor="#F44336" value={avgHeartRate.toString()} label="Ort. Nabız (bpm)" />
              <StatCard icon={Activity} iconColor="#FF6047" value={maxHeartRate.toString()} label="Maks. Nabız (bpm)" />
            </View>

            <Text style={[styles.sectionLabel, { marginTop: 20 }]}>KALBİN ATTIĞI BÖLGELER</Text>
            <View style={styles.hrZonesCard}>
              {[
                { name: 'Dinlenme', range: '< 50%', color: '#9E9E9E', width: '10%' },
                { name: 'Hafif', range: '50–60%', color: '#4CAF50', width: '15%' },
                { name: 'Aerobik', range: '60–70%', color: '#2196F3', width: '40%' },
                { name: 'Anaerobik', range: '70–85%', color: '#FF9800', width: '25%' },
                { name: 'Maks.', range: '85%+', color: '#F44336', width: '10%' },
              ].map((zone, idx) => (
                <View key={idx} style={styles.hrZoneRow}>
                  <View style={[styles.hrZoneDot, { backgroundColor: zone.color }]} />
                  <View style={styles.hrZoneTextCol}>
                    <Text style={styles.hrZoneName}>{zone.name}</Text>
                    <Text style={styles.hrZoneRange}>{zone.range}</Text>
                  </View>
                  <View style={styles.hrZoneBarBg}>
                    <View style={[styles.hrZoneBar, { backgroundColor: zone.color, width: zone.width as any }]} />
                  </View>
                </View>
              ))}
            </View>
          </>
        )}

        {/* Mesafe */}
        {distanceKm > 0 && (
          <>
            <Text style={[styles.sectionLabel, { marginTop: 20 }]}>MESAFE VERİLERİ</Text>
            <View style={styles.row}>
              <StatCard icon={MapPin} iconColor="#4CAF50" value={`${distanceKm.toFixed(2)} km`} label="Toplam Mesafe" />
              <StatCard icon={Timer} iconColor="#9C27B0" value={`${Math.floor(avgPaceMinPerKm)}'${Math.round((avgPaceMinPerKm % 1) * 60)}"`} label="Ort. Tempo" />
            </View>

            {route && route.length > 0 && (
              <View style={styles.mapSummaryCard}>
                 <Navigation size={32} color={Colors.primary} />
                 <Text style={styles.mapText}>GPS Rota Özeti</Text>
                 <Text style={styles.mapSubtext}>{route.length} konum noktası</Text>
              </View>
            )}

            <Text style={[styles.sectionLabel, { marginTop: 20 }]}>KM BAŞI BÖLÜNMELER</Text>
            <View style={styles.splitsCard}>
              <View style={styles.splitHeader}>
                <Text style={styles.splitHeaderText}>KM</Text>
                <Text style={styles.splitHeaderText}>TEMPO</Text>
                <View style={{ flex: 1 }} />
              </View>
              {splits.map((s: any, idx: number) => (
                <View key={idx} style={[styles.splitRow, idx % 2 === 0 && { backgroundColor: 'transparent' }]}>
                  <Text style={styles.splitKm}>{s.km}</Text>
                  <Text style={styles.splitPace}>{Math.floor(s.pace)}'{Math.round((s.pace % 1) * 60)}"</Text>
                  <View style={styles.splitBarBg}>
                    <View style={[styles.splitBar, { width: `${100 - (s.pace - 5) * 40}%`, backgroundColor: s.pace < 5.4 ? '#4CAF50' : '#FF9800' }]} />
                  </View>
                </View>
              ))}
            </View>
          </>
        )}

        {/* Efor */}
        {perceivedEffort && (
          <View style={styles.effortCard}>
            <Text style={{ fontSize: 28 }}>🥵</Text>
            <View style={{ marginLeft: 12 }}>
              <Text style={styles.effortLabel}>Nasıl hissettin?</Text>
              <Text style={styles.effortValue}>{perceivedEffort}</Text>
            </View>
          </View>
        )}

        {/* Overload */}
        <View style={styles.overloadCard}>
          <View style={styles.overloadIconBox}>
            <TrendingUp size={22} color="#4CAF50" />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.overloadTitle}>Aşamalı Yüklenme Önerisi</Text>
            <Text style={styles.overloadDesc}>Tüm setleri tamamladın — bir sonraki seansta 2.5 kg dene.</Text>
          </View>
        </View>

      </ScrollView>

      {/* Bottom Bar */}
      <View style={styles.bottomBar}>
        <TouchableOpacity style={styles.shareButton} onPress={() => setShowShareSheet(true)}>
          <Share2 size={18} color={Colors.primary} />
        </TouchableOpacity>
        <TouchableOpacity style={styles.closeButton} onPress={() => navigation.goBack()}>
          <Text style={styles.closeButtonText}>Kapat</Text>
        </TouchableOpacity>
      </View>

      <WorkoutShareSheet 
        visible={showShareSheet}
        training={training}
        durationSeconds={durationSeconds}
        distanceKm={distanceKm}
        totalCalories={calories}
        onDismiss={() => setShowShareSheet(false)}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  scrollContent: {
    paddingBottom: 100,
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 48,
    paddingBottom: 20,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.allWhite,
    textAlign: 'center',
    marginBottom: 16,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  emojiBox: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: 'rgba(255, 96, 71, 0.12)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTextCol: {
    flex: 1,
    marginLeft: 12,
  },
  trainingTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  trainingDate: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  checkBox: {
    width: 40,
    height: 40,
    borderRadius: 8,
    backgroundColor: 'rgba(76, 175, 80, 0.12)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.textSecondaryDark,
    letterSpacing: 1.5,
    marginHorizontal: 20,
    marginBottom: 8,
  },
  row: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    gap: 12,
  },
  statCard: {
    flex: 1,
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
  },
  statIconBox: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  statValue: {
    fontSize: 22,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  statLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
  hrZonesCard: {
    marginHorizontal: 20,
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    gap: 12,
  },
  hrZoneRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  hrZoneDot: {
    width: 6,
    height: 20,
    borderRadius: 3,
  },
  hrZoneTextCol: {
    width: 90,
    marginLeft: 10,
  },
  hrZoneName: {
    fontSize: 13,
    fontWeight: '500',
    color: Colors.allWhite,
  },
  hrZoneRange: {
    fontSize: 10,
    color: Colors.textSecondaryDark,
  },
  hrZoneBarBg: {
    flex: 1,
    height: 8,
    backgroundColor: 'rgba(255,255,255,0.05)',
    borderRadius: 4,
    marginLeft: 10,
  },
  hrZoneBar: {
    height: '100%',
    borderRadius: 4,
  },
  splitsCard: {
    marginHorizontal: 20,
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    overflow: 'hidden',
  },
  splitHeader: {
    flexDirection: 'row',
    backgroundColor: 'rgba(255,255,255,0.05)',
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  splitHeaderText: {
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.textSecondaryDark,
    width: 50,
  },
  splitRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'rgba(255,255,255,0.02)',
  },
  splitKm: {
    fontSize: 14,
    fontWeight: 'bold',
    color: Colors.allWhite,
    width: 50,
  },
  splitPace: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.allWhite,
    width: 60,
  },
  splitBarBg: {
    flex: 1,
    height: 8,
    backgroundColor: 'rgba(255,255,255,0.05)',
    borderRadius: 4,
  },
  splitBar: {
    height: '100%',
    borderRadius: 4,
  },
  effortCard: {
    marginHorizontal: 20,
    marginTop: 20,
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },
  effortLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
  },
  effortValue: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.allWhite,
  },
  overloadCard: {
    marginHorizontal: 20,
    marginTop: 20,
    backgroundColor: 'rgba(76, 175, 80, 0.06)',
    borderRadius: 16,
    padding: 14,
    flexDirection: 'row',
    alignItems: 'center',
  },
  overloadIconBox: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(76, 175, 80, 0.12)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 14,
  },
  overloadTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.allWhite,
  },
  overloadDesc: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 3,
  },
  bottomBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: Colors.backgroundDark,
    flexDirection: 'row',
    paddingHorizontal: 20,
    paddingVertical: 16,
    paddingBottom: 32,
    gap: 12,
  },
  shareButton: {
    width: 52,
    height: 52,
    borderRadius: 26,
    borderWidth: 1.5,
    borderColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeButton: {
    flex: 1,
    height: 52,
    borderRadius: 26,
    backgroundColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.allWhite,
  },
  mapSummaryCard: {
    marginHorizontal: 20,
    marginTop: 20,
    height: 180,
    backgroundColor: 'rgba(0,0,0,0.3)',
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  mapText: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.primary,
    marginTop: 8,
  },
  mapSubtext: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 4,
  },
});
