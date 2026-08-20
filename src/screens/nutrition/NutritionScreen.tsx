import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Svg, { Circle } from 'react-native-svg';
import { ChevronDown, ChevronUp, ArrowLeft, Apple, Utensils } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { Collapsible } from '../../components/motion/Collapsible';
import { AuthService } from '../../services/authService';
import { NutritionService, NutritionPlan } from '../../services/nutritionService';

export const NutritionScreen = ({ navigation }: any) => {
  const [loading, setLoading] = useState(true);
  const [plan, setPlan] = useState<NutritionPlan | null>(null);
  const [expandedMeals, setExpandedMeals] = useState<Record<string, boolean>>({});

  useEffect(() => {
    loadNutritionPlan();
  }, []);

  const loadNutritionPlan = async () => {
    setLoading(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const activePlan = await NutritionService.fetchActivePlanForUser(user.id);
      setPlan(activePlan);
      if (activePlan?.meals) {
        const initialExpand: Record<string, boolean> = {};
        activePlan.meals.forEach((m, idx) => {
          if (idx === 0) initialExpand[m.id] = true;
        });
        setExpandedMeals(initialExpand);
      }
    } catch (e) {
      console.error('Error loading nutrition plan:', e);
    } finally {
      setLoading(false);
    }
  };

  const toggleMeal = (id: string) => {
    setExpandedMeals((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  // Toplam planlanan kaloriler ve makrolar
  const targetCalories = plan?.target_calories || 2000;
  const totalMealCalories = (plan?.meals || []).reduce((sum, m) => sum + (m.calories || 0), 0);
  const totalProtein = (plan?.meals || []).reduce((sum, m) => sum + (Number(m.protein) || 0), 0);
  const totalCarbs = (plan?.meals || []).reduce((sum, m) => sum + (Number(m.carbs) || 0), 0);
  const totalFat = (plan?.meals || []).reduce((sum, m) => sum + (Number(m.fat) || 0), 0);

  // SVG hesaplamaları
  const radius = 60;
  const strokeWidth = 12;
  const circumference = 2 * Math.PI * radius;
  const progress = Math.min(totalMealCalories / (targetCalories || 1), 1);
  const strokeDashoffset = circumference - circumference * progress;

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
          <ArrowLeft size={22} color={Colors.textDark} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Beslenme Programım</Text>
      </View>

      {loading ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : !plan ? (
        <View style={styles.emptyState}>
          <Utensils size={48} color={Colors.textSecondaryDark} />
          <Text style={styles.emptyTitle}>Atanmış Beslenme Planı Yok</Text>
          <Text style={styles.emptySubtitle}>
            Eğitmeniniz tarafından size özel bir beslenme veya diyet programı atandığında burada görünecektir.
          </Text>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
          {/* Plan Başlığı & Açıklaması */}
          <View style={styles.planHeaderCard}>
            <Text style={styles.planName}>{plan.name}</Text>
            {plan.description ? (
              <Text style={styles.planDesc}>{plan.description}</Text>
            ) : null}
          </View>

          {/* Kalori Halkası */}
          <View style={styles.calorieCard}>
            <Text style={styles.cardTitle}>Günlük Hedef & Dağılım</Text>
            <View style={styles.calorieRingContainer}>
              <View style={styles.svgWrapper}>
                <Svg width={150} height={150} viewBox="0 0 150 150">
                  <Circle
                    cx="75"
                    cy="75"
                    r={radius}
                    stroke={Colors.borderDark}
                    strokeWidth={strokeWidth}
                    fill="none"
                  />
                  <Circle
                    cx="75"
                    cy="75"
                    r={radius}
                    stroke={Colors.primary}
                    strokeWidth={strokeWidth}
                    fill="none"
                    strokeDasharray={`${circumference} ${circumference}`}
                    strokeDashoffset={strokeDashoffset}
                    strokeLinecap="round"
                    transform="rotate(-90 75 75)"
                  />
                </Svg>
                <View style={styles.calorieTextContainer}>
                  <Text style={styles.consumedText}>{totalMealCalories}</Text>
                  <Text style={styles.goalText}>/ {targetCalories} kcal</Text>
                </View>
              </View>

              {/* Makro Barları */}
              <View style={styles.macroList}>
                <View style={styles.macroItem}>
                  <View style={styles.macroHeader}>
                    <Text style={styles.macroLabel}>Protein</Text>
                    <Text style={styles.macroValues}>{Math.round(totalProtein)}g</Text>
                  </View>
                  <View style={styles.progressBarBg}>
                    <View
                      style={[
                        styles.progressBarFill,
                        {
                          width: `${Math.min((totalProtein / 180) * 100, 100)}%`,
                          backgroundColor: '#4ade80',
                        },
                      ]}
                    />
                  </View>
                </View>

                <View style={styles.macroItem}>
                  <View style={styles.macroHeader}>
                    <Text style={styles.macroLabel}>Karbonhidrat</Text>
                    <Text style={styles.macroValues}>{Math.round(totalCarbs)}g</Text>
                  </View>
                  <View style={styles.progressBarBg}>
                    <View
                      style={[
                        styles.progressBarFill,
                        {
                          width: `${Math.min((totalCarbs / 250) * 100, 100)}%`,
                          backgroundColor: '#60a5fa',
                        },
                      ]}
                    />
                  </View>
                </View>

                <View style={styles.macroItem}>
                  <View style={styles.macroHeader}>
                    <Text style={styles.macroLabel}>Yağ</Text>
                    <Text style={styles.macroValues}>{Math.round(totalFat)}g</Text>
                  </View>
                  <View style={styles.progressBarBg}>
                    <View
                      style={[
                        styles.progressBarFill,
                        {
                          width: `${Math.min((totalFat / 80) * 100, 100)}%`,
                          backgroundColor: '#f87171',
                        },
                      ]}
                    />
                  </View>
                </View>
              </View>
            </View>
          </View>

          {/* Öğünler Listesi */}
          <Text style={styles.sectionTitle}>Öğün Planı ({plan.meals?.length || 0} Öğün)</Text>
          {plan.meals && plan.meals.length > 0 ? (
            plan.meals.map((meal) => {
              const isExpanded = !!expandedMeals[meal.id];
              return (
                <View key={meal.id} style={styles.mealCard}>
                  <TouchableOpacity
                    style={styles.mealHeader}
                    onPress={() => toggleMeal(meal.id)}
                    activeOpacity={0.7}
                  >
                    <View style={styles.mealTitleRow}>
                      <View style={styles.iconCircle}>
                        <Apple size={18} color={Colors.primary} />
                      </View>
                      <View>
                        <Text style={styles.mealName}>{meal.name}</Text>
                        <Text style={styles.mealCalories}>{meal.calories} kcal</Text>
                      </View>
                    </View>
                    {isExpanded ? (
                      <ChevronUp size={20} color={Colors.textSecondaryDark} />
                    ) : (
                      <ChevronDown size={20} color={Colors.textSecondaryDark} />
                    )}
                  </TouchableOpacity>

                  <Collapsible expanded={isExpanded}>
                    <View style={styles.mealBody}>
                      {meal.description ? (
                        <Text style={styles.mealDescriptionText}>{meal.description}</Text>
                      ) : null}

                      {meal.foods && meal.foods.length > 0 ? (
                        <View style={styles.foodList}>
                          {meal.foods.map((food) => (
                            <View key={food.id} style={styles.foodItem}>
                              <View style={{ flex: 1 }}>
                                <Text style={styles.foodName}>{food.name}</Text>
                                {food.portion ? (
                                  <Text style={styles.foodPortion}>{food.portion}</Text>
                                ) : null}
                              </View>
                              <Text style={styles.foodCal}>{food.calories} kcal</Text>
                            </View>
                          ))}
                        </View>
                      ) : null}
                    </View>
                  </Collapsible>
                </View>
              );
            })
          ) : (
            <View style={{ padding: 16, alignItems: 'center' }}>
              <Text style={{ color: Colors.textSecondaryDark }}>Bu planda henüz öğün tanımlanmamış.</Text>
            </View>
          )}
        </ScrollView>
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderDark,
    gap: 12,
  },
  backBtn: {
    padding: 4,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  scrollContent: {
    padding: 16,
    gap: 14,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  planHeaderCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    gap: 6,
  },
  planName: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.textDark,
  },
  planDesc: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    lineHeight: 18,
  },
  calorieCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    gap: 12,
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textDark,
  },
  calorieRingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  svgWrapper: {
    position: 'relative',
    width: 150,
    height: 150,
    alignItems: 'center',
    justifyContent: 'center',
  },
  calorieTextContainer: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  consumedText: {
    fontSize: 20,
    fontWeight: '800',
    color: Colors.textDark,
  },
  goalText: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
  },
  macroList: {
    flex: 1,
    gap: 10,
  },
  macroItem: {
    gap: 4,
  },
  macroHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  macroLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    fontWeight: '500',
  },
  macroValues: {
    fontSize: 12,
    color: Colors.textDark,
    fontWeight: '600',
  },
  progressBarBg: {
    height: 6,
    backgroundColor: Colors.borderDark,
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: '100%',
    borderRadius: 3,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
    marginTop: 6,
  },
  mealCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Colors.borderDark,
    overflow: 'hidden',
  },
  mealHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 14,
  },
  mealTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  iconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  mealName: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textDark,
  },
  mealCalories: {
    fontSize: 12,
    color: Colors.primary,
    marginTop: 2,
    fontWeight: '500',
  },
  mealBody: {
    paddingHorizontal: 14,
    paddingBottom: 14,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
    gap: 8,
  },
  mealDescriptionText: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    marginTop: 8,
    lineHeight: 18,
  },
  foodList: {
    gap: 6,
    marginTop: 4,
  },
  foodItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.05)',
  },
  foodName: {
    fontSize: 13,
    color: Colors.textDark,
    fontWeight: '500',
  },
  foodPortion: {
    fontSize: 11,
    color: Colors.textSecondaryDark,
  },
  foodCal: {
    fontSize: 12,
    color: Colors.primary,
    fontWeight: '600',
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
    gap: 12,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  emptySubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    lineHeight: 18,
  },
});
