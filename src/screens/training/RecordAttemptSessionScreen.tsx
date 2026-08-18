import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Animated,
  ActivityIndicator,
} from 'react-native';
import { SmoothModal } from '../../components/motion/SmoothModal';
import { Colors } from '../../theme/colors';
import { ArrowLeft, X, Flame, Check, AlertCircle } from 'lucide-react-native';
import { supabase } from '../../services/supabaseClient';
import { useAuth } from '../../context/AuthContext';
import { feedback } from '../../services/feedbackService';
import { RecordAttemptService } from '../../services/recordAttemptService';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

export const RecordAttemptSessionScreen = ({ route, navigation }: any) => {
  const insets = useSafeAreaInsets();
  const { session } = useAuth();
  const userId = session?.user?.id;

  const { attempt, exercise, catalogExercise, recordType, targetValue, targetReps, plan } = route.params || {};

  const [currentSetIndex, setCurrentSetIndex] = useState(0);
  const [showRpeModal, setShowRpeModal] = useState(false);
  const [selectedRpe, setSelectedRpe] = useState(8);
  const [pendingSuccess, setPendingSuccess] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleAbandon = async () => {
    if (isSubmitting) return;
    const confirmed = await feedback.destructive({
      title: 'Rekor Denemesinden Çık?',
      message: 'Devam eden rekor denemesi sonlandırılacak. Çıkmak istediğinize emin misiniz?',
      confirmText: 'Çıkış Yap',
      cancelText: 'Vazgeç',
    });
    if (confirmed) {
      if (attempt?.id) {
        setIsSubmitting(true);
        try {
          await RecordAttemptService.abandonAttempt(attempt.id);
          navigation.goBack();
        } catch (e: any) {
          console.error('[RecordAttemptSessionScreen] Failed to abandon attempt:', e);
          feedback.error({
            title: 'Çıkış Başarısız',
            message: e,
            fallbackMessage: 'Rekor denemesi sonlandırılamadı. Lütfen tekrar deneyin.',
          });
        } finally {
          setIsSubmitting(false);
        }
      } else {
        navigation.goBack();
      }
    }
  };

  const setList = plan && plan.length > 0 ? plan : [{ weight: targetValue || 100, reps: targetReps || 1, type: 'main' }];
  const currentSet = setList[currentSetIndex] || setList[0];
  const isMainSet = currentSet?.set_type === 'main' || currentSet?.type === 'main' || currentSetIndex === setList.length - 1;

  const weightVal = currentSet?.prescribed_weight ?? currentSet?.weight ?? targetValue ?? 100;
  const repsVal = currentSet?.prescribed_reps ?? currentSet?.reps ?? targetReps ?? 1;

  const handleYaptimClick = () => {
    setPendingSuccess(true);
    setShowRpeModal(true);
  };

  const handleYapamadamClick = () => {
    setPendingSuccess(false);
    setShowRpeModal(true);
  };

  const handleConfirmRpe = async (rpeScore: number) => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    const isSuccess = pendingSuccess;

    try {
      // 1. Update current set row if setId is present
      if (currentSet?.id) {
        await RecordAttemptService.saveSet(
          currentSet.id,
          weightVal,
          repsVal,
          rpeScore,
          90,
          `Set ${currentSetIndex + 1} - RPE: ${rpeScore}`
        );
      }

      // 2. Check if we have more sets (warmups)
      if (currentSetIndex < setList.length - 1) {
        setShowRpeModal(false);
        setCurrentSetIndex(currentSetIndex + 1);
      } else {
        // 3. Final Main Set: complete or fail the attempt
        let isNewPR = false;
        if (attempt?.id && userId) {
          await RecordAttemptService.completeAttempt(
            attempt.id,
            isSuccess,
            `RPE: ${rpeScore}`,
            userId
          );

          if (isSuccess && exercise?.id) {
            const evalResult = await RecordAttemptService.evaluateAndSavePersonalRecord(
              userId,
              exercise.id,
              {
                resultType: 'weight',
                exerciseId: exercise.id,
                catalogId: catalogExercise?.id,
                weightKg: weightVal,
                reps: repsVal,
                rpe: rpeScore,
                notes: `Rekor Denemesi RPE: ${rpeScore}`,
              }
            );
            isNewPR = evalResult.isNewPR;
          }
        }

        setShowRpeModal(false);
        navigation.navigate('RecordAttemptSummary', {
          attempt,
          exercise,
          recordType: 'weight',
          targetValue: weightVal,
          targetReps: repsVal,
          success: isSuccess,
          isNewPR,
          rpe: rpeScore,
        });
      }
    } catch (e: any) {
      console.error('[RecordAttemptSessionScreen] Error saving attempt set:', e);
      feedback.error({
        title: 'Kayıt Hatası',
        message: e,
        fallbackMessage: 'Set kaydedilemedi. Lütfen tekrar deneyin.',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const getRpeBadgeStyle = (num: number) => {
    if (num <= 3) return { backgroundColor: 'rgba(76,175,80,0.2)', borderColor: '#4CAF50', textColor: '#4CAF50' };
    if (num <= 7) return { backgroundColor: 'rgba(255,152,0,0.2)', borderColor: '#FF9800', textColor: '#FF9800' };
    return { backgroundColor: 'rgba(244,67,54,0.2)', borderColor: '#F44336', textColor: '#F44336' };
  };

  return (
    <View style={styles.container}>
      {/* ── Header ────────────────────────────────────────────────────────── */}
      <View style={[styles.headerRow, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity
          onPress={handleAbandon}
          style={styles.headerIconBtn}
          activeOpacity={0.8}
        >
          <ArrowLeft size={20} color={Colors.textDark} />
        </TouchableOpacity>

        <Text style={styles.headerTitle} numberOfLines={1}>
          {exercise?.name || 'Back Squat'}
        </Text>

        <TouchableOpacity
          onPress={handleAbandon}
          style={styles.headerIconBtn}
          activeOpacity={0.8}
        >
          <X size={20} color={Colors.textDark} />
        </TouchableOpacity>
      </View>
      <View style={styles.headerDividerLine} />

      <ScrollView contentContainerStyle={[styles.scrollContent, { paddingBottom: 100 + insets.bottom }]} showsVerticalScrollIndicator={false}>
        {/* ── Flame Cluster Visual ────────────────────────────────────────── */}
        <View style={styles.flameCluster}>
          <Flame size={20} color="rgba(255,152,0,0.6)" style={{ marginBottom: 4 }} />
          <Flame size={44} color="#FFFFFF" />
          <Flame size={20} color="rgba(255,152,0,0.6)" style={{ marginBottom: 4 }} />
        </View>

        {/* ── Center Weight Circle (Image 2 Matching) ────────────────────── */}
        <View style={styles.circleOuterGlow}>
          <View style={styles.circleInner}>
            <Text style={styles.circlePrimaryVal}>{weightVal.toString().replace('.', ',')}</Text>
            <Text style={styles.circleUnitText}>
              {recordType === 'reps' ? 'tekrar' : recordType === 'time' ? 'sn' : 'kg'}
            </Text>
          </View>
        </View>

        {/* ── Subtitle (ANA DENEME 1/1) ──────────────────────────────────── */}
        <Text style={styles.sessionStepLabel}>
          {isMainSet ? `ANA DENEME ${currentSetIndex + 1}/${setList.length}` : `ISINMA SETİ ${currentSetIndex + 1}/${setList.length}`}
        </Text>
      </ScrollView>

      {/* ── Bottom Action Buttons ────────────────────────────────────────── */}
      <View style={[styles.bottomBar, { bottom: Math.max(20, insets.bottom + 12) }]}>
        <TouchableOpacity
          style={[styles.actionBtn, styles.failBtn]}
          onPress={handleYapamadamClick}
          activeOpacity={0.8}
        >
          <X size={20} color={Colors.allWhite} />
          <Text style={styles.actionBtnText}>Yapamadım</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.actionBtn, styles.successBtn]}
          onPress={handleYaptimClick}
          activeOpacity={0.8}
        >
          <Flame size={20} color={Colors.allWhite} />
          <Text style={styles.actionBtnText}>Yaptım!</Text>
        </TouchableOpacity>
      </View>

      {/* ── RPE Zorluk Puanı Popup Modal ─────────────────────────────────── */}
      <SmoothModal
        visible={showRpeModal}
        onClose={() => setShowRpeModal(false)}
        variant="modal"
      >
        <View style={styles.modalCard}>
          <View style={styles.modalHeaderRow}>
            <Flame size={28} color={Colors.primary} />
            <Text style={styles.modalTitle}>RPE Zorluk Derecesi</Text>
          </View>
          <Text style={styles.modalSub}>
            Bu deneme seni ne kadar zorladı? Zorluk derecesini (1-10) seçin.
          </Text>

          {/* 1 - 10 Score Rating Buttons Grid */}
          <View style={styles.rpeGrid}>
            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((num) => {
              const badgeStyle = getRpeBadgeStyle(num);
              const isSelected = selectedRpe === num;
              return (
                <TouchableOpacity
                  key={num}
                  style={[
                    styles.rpeItemBtn,
                    isSelected && {
                      backgroundColor: Colors.primary,
                      borderColor: Colors.primary,
                    },
                  ]}
                  onPress={() => setSelectedRpe(num)}
                  activeOpacity={0.8}
                >
                  <Text
                    style={[
                      styles.rpeItemNumText,
                      isSelected && { color: Colors.allWhite, fontWeight: '800' },
                    ]}
                  >
                    {num}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Selected Rating Description */}
          <View style={styles.selectedRpeInfoBox}>
            <Text style={styles.selectedRpeInfoText}>
              {selectedRpe <= 3
                ? '🟢 Çok Kolay (RPE 1-3) · Zahmetsizce yapıldı'
                : selectedRpe <= 7
                ? '🟠 Orta / İdeal (RPE 4-7) · Zorladı ama form bozulmadı'
                : '🔴 Maksimum Efor (RPE 8-10) · Son limite kadar zorlandı'}
            </Text>
          </View>

          {/* Modal Actions */}
          <TouchableOpacity
            style={[styles.modalConfirmBtn, isSubmitting && { opacity: 0.7 }]}
            onPress={() => handleConfirmRpe(selectedRpe)}
            activeOpacity={0.8}
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <ActivityIndicator size="small" color={Colors.allWhite} />
            ) : (
              <>
                <Check size={20} color={Colors.allWhite} />
                <Text style={styles.modalConfirmBtnText}>Kaydet ve Devam Et</Text>
              </>
            )}
          </TouchableOpacity>
        </View>
      </SmoothModal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0D0300', // Match dark fire background
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 50,
    paddingBottom: 14,
  },
  headerIconBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.08)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    flex: 1,
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
    textAlign: 'center',
    marginHorizontal: 10,
  },
  headerDividerLine: {
    height: 1,
    backgroundColor: 'rgba(255,96,71,0.2)',
    width: '100%',
  },
  scrollContent: {
    alignItems: 'center',
    paddingTop: 40,
    paddingBottom: 120,
  },
  flameCluster: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: -4,
    marginBottom: 20,
  },
  circleOuterGlow: {
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,96,71,0.08)',
    borderWidth: 2,
    borderColor: 'rgba(255,96,71,0.3)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  circleInner: {
    width: 190,
    height: 190,
    borderRadius: 95,
    backgroundColor: '#1E0A03',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,96,71,0.2)',
  },
  circlePrimaryVal: {
    fontSize: 48,
    fontWeight: '900',
    color: Colors.allWhite,
  },
  circleUnitText: {
    fontSize: 16,
    color: 'rgba(255,255,255,0.4)',
    fontWeight: '600',
    marginTop: 4,
  },
  sessionStepLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: 'rgba(255,255,255,0.4)',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
  },
  bottomBar: {
    position: 'absolute',
    bottom: 30,
    left: 20,
    right: 20,
    flexDirection: 'row',
    gap: 14,
  },
  actionBtn: {
    flex: 1,
    height: 56,
    borderRadius: 100,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    elevation: 4,
  },
  failBtn: {
    backgroundColor: '#D32F2F', // Clean red
  },
  successBtn: {
    backgroundColor: Colors.primary, // Primary coral/orange
  },
  actionBtnText: {
    color: Colors.allWhite,
    fontSize: 17,
    fontWeight: '700',
  },
  // Modal Popup Styles
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 20,
  },
  modalCard: {
    width: '100%',
    backgroundColor: Colors.cardDark,
    borderRadius: 24,
    padding: 24,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    elevation: 10,
  },
  modalHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 8,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
  },
  modalTitleCentral: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.textDark,
    textAlign: 'center',
    marginBottom: 6,
  },
  modalSub: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginBottom: 20,
    lineHeight: 18,
  },
  modalSubCentral: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    marginBottom: 20,
  },
  rpeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    justifyContent: 'center',
    marginBottom: 16,
  },
  rpeItemBtn: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: Colors.backgroundDark,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    justifyContent: 'center',
    alignItems: 'center',
  },
  rpeItemNumText: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  selectedRpeInfoBox: {
    backgroundColor: 'rgba(255,96,71,0.08)',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
    marginBottom: 20,
  },
  selectedRpeInfoText: {
    fontSize: 12,
    color: Colors.primary,
    fontWeight: '600',
    textAlign: 'center',
  },
  modalConfirmBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: Colors.primary,
    paddingVertical: 14,
    borderRadius: 100,
  },
  modalConfirmBtnText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: '700',
  },
  abandonModalBtnRow: {
    flexDirection: 'row',
    gap: 12,
  },
  abandonCancelBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 100,
    backgroundColor: Colors.backgroundDark,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  abandonCancelBtnText: {
    color: Colors.textDark,
    fontWeight: '600',
    fontSize: 14,
  },
  abandonConfirmBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 100,
    backgroundColor: Colors.error,
    alignItems: 'center',
  },
  abandonConfirmBtnText: {
    color: Colors.allWhite,
    fontWeight: '700',
    fontSize: 14,
  },
});
