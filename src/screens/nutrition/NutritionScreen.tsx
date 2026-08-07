import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Modal, TextInput, SafeAreaView, Platform } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import { ChevronDown, ChevronUp, Plus, X } from 'lucide-react-native';
import { Colors } from '../../theme/colors';

const NUTRITION_GOAL = 2500;
const CONSUMED = 1850;

const MACROS = {
  protein: { current: 120, target: 150, color: '#4ade80', label: 'Protein' },
  carbs: { current: 200, target: 300, color: '#60a5fa', label: 'Karbonhidrat' },
  fat: { current: 45, target: 80, color: '#f87171', label: 'Yağ' },
};

const INITIAL_MEALS = [
  { id: '1', name: 'Kahvaltı', calories: 450, items: [{ name: 'Yulaf', cal: 300 }, { name: 'Süt', cal: 150 }] },
  { id: '2', name: 'Öğle', calories: 600, items: [{ name: 'Tavuk', cal: 400 }, { name: 'Pirinç', cal: 200 }] },
  { id: '3', name: 'Akşam', calories: 800, items: [{ name: 'Balık', cal: 500 }, { name: 'Salata', cal: 300 }] },
  { id: '4', name: 'Ara Öğün', calories: 0, items: [] },
];

export const NutritionScreen = () => {
  const [meals, setMeals] = useState(INITIAL_MEALS);
  const [expandedMeals, setExpandedMeals] = useState<Record<string, boolean>>({});
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedMealId, setSelectedMealId] = useState<string | null>(null);
  
  const [foodName, setFoodName] = useState('');
  const [foodCalories, setFoodCalories] = useState('');

  const toggleMeal = (id: string) => {
    setExpandedMeals(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const openAddFoodModal = (id: string) => {
    setSelectedMealId(id);
    setFoodName('');
    setFoodCalories('');
    setModalVisible(true);
  };

  const handleAddFood = () => {
    if (selectedMealId && foodName && foodCalories) {
      setMeals(prev => prev.map(meal => {
        if (meal.id === selectedMealId) {
          const cal = parseInt(foodCalories, 10) || 0;
          return {
            ...meal,
            calories: meal.calories + cal,
            items: [...meal.items, { name: foodName, cal }]
          };
        }
        return meal;
      }));
    }
    setModalVisible(false);
  };

  // SVG hesaplamaları
  const radius = 60;
  const strokeWidth = 12;
  const circumference = 2 * Math.PI * radius;
  const progress = Math.min(CONSUMED / NUTRITION_GOAL, 1);
  const strokeDashoffset = circumference - (circumference * progress);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.headerTitle}>Beslenme & Diyet</Text>
        
        {/* Kalori Halkası */}
        <View style={styles.calorieCard}>
          <Text style={styles.cardTitle}>Günlük Kalori Özeti</Text>
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
                  strokeDasharray={circumference}
                  strokeDashoffset={strokeDashoffset}
                  strokeLinecap="round"
                  rotation="-90"
                  origin="75, 75"
                />
              </Svg>
              <View style={styles.calorieTextContainer}>
                <Text style={styles.calorieValue}>{CONSUMED}</Text>
                <Text style={styles.calorieLabel}>/ {NUTRITION_GOAL} kcal</Text>
              </View>
            </View>
          </View>
        </View>

        {/* Makro Dağılım Kartı */}
        <View style={styles.macroCard}>
          <Text style={styles.cardTitle}>Makro Dağılımı</Text>
          {Object.entries(MACROS).map(([key, data]) => {
            const macroProgress = Math.min(data.current / data.target, 1) * 100;
            return (
              <View key={key} style={styles.macroRow}>
                <View style={styles.macroInfo}>
                  <Text style={styles.macroLabel}>{data.label}</Text>
                  <Text style={styles.macroValue}>{data.current}g / {data.target}g</Text>
                </View>
                <View style={styles.macroBarBg}>
                  <View style={[styles.macroBarFill, { width: `${macroProgress}%`, backgroundColor: data.color }]} />
                </View>
              </View>
            );
          })}
        </View>

        {/* Öğün Kartları */}
        <Text style={styles.sectionTitle}>Öğünler</Text>
        {meals.map(meal => {
          const isExpanded = expandedMeals[meal.id];
          return (
            <View key={meal.id} style={styles.mealCard}>
              <TouchableOpacity style={styles.mealHeader} onPress={() => toggleMeal(meal.id)}>
                <View>
                  <Text style={styles.mealName}>{meal.name}</Text>
                  <Text style={styles.mealCalories}>{meal.calories} kcal</Text>
                </View>
                {isExpanded ? <ChevronUp color={Colors.textSecondaryDark} /> : <ChevronDown color={Colors.textSecondaryDark} />}
              </TouchableOpacity>
              
              {isExpanded && (
                <View style={styles.mealDetails}>
                  {meal.items.map((item, idx) => (
                    <View key={idx} style={styles.foodItem}>
                      <Text style={styles.foodName}>{item.name}</Text>
                      <Text style={styles.foodCalories}>{item.cal} kcal</Text>
                    </View>
                  ))}
                  <TouchableOpacity style={styles.addFoodButton} onPress={() => openAddFoodModal(meal.id)}>
                    <Plus size={16} color={Colors.primary} />
                    <Text style={styles.addFoodText}>Besin Ekle</Text>
                  </TouchableOpacity>
                </View>
              )}
            </View>
          );
        })}
      </ScrollView>

      {/* Besin Ekleme Modal */}
      <Modal visible={modalVisible} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Yeni Besin Ekle</Text>
              <TouchableOpacity onPress={() => setModalVisible(false)}>
                <X color={Colors.textSecondaryDark} />
              </TouchableOpacity>
            </View>
            <TextInput
              style={styles.input}
              placeholder="Besin Adı"
              placeholderTextColor={Colors.textSecondaryDark}
              value={foodName}
              onChangeText={setFoodName}
            />
            <TextInput
              style={styles.input}
              placeholder="Kalori (kcal)"
              placeholderTextColor={Colors.textSecondaryDark}
              keyboardType="numeric"
              value={foodCalories}
              onChangeText={setFoodCalories}
            />
            <TouchableOpacity style={styles.saveButton} onPress={handleAddFood}>
              <Text style={styles.saveButtonText}>Ekle</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.backgroundDark,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginBottom: 20,
    marginTop: Platform.OS === 'android' ? 20 : 0,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.allWhite,
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
    marginTop: 10,
    marginBottom: 12,
  },
  calorieCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    alignItems: 'center',
  },
  calorieRingContainer: {
    alignItems: 'center',
    justifyContent: 'center',
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
  calorieValue: {
    fontSize: 28,
    fontWeight: 'bold',
    color: Colors.primary,
  },
  calorieLabel: {
    fontSize: 12,
    color: Colors.textSecondaryDark,
    marginTop: 2,
  },
  macroCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
  },
  macroRow: {
    marginBottom: 12,
  },
  macroInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  macroLabel: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
  },
  macroValue: {
    fontSize: 14,
    color: Colors.allWhite,
    fontWeight: '500',
  },
  macroBarBg: {
    height: 8,
    backgroundColor: Colors.borderDark,
    borderRadius: 4,
    overflow: 'hidden',
  },
  macroBarFill: {
    height: '100%',
    borderRadius: 4,
  },
  mealCard: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    marginBottom: 12,
    overflow: 'hidden',
  },
  mealHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
  },
  mealName: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.allWhite,
  },
  mealCalories: {
    fontSize: 14,
    color: Colors.primary,
    marginTop: 4,
  },
  mealDetails: {
    padding: 16,
    paddingTop: 0,
    borderTopWidth: 1,
    borderTopColor: Colors.borderDark,
  },
  foodItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: Colors.backgroundDark,
  },
  foodName: {
    fontSize: 14,
    color: Colors.allWhite,
  },
  foodCalories: {
    fontSize: 14,
    color: Colors.textSecondaryDark,
  },
  addFoodButton: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
  },
  addFoodText: {
    fontSize: 14,
    color: Colors.primary,
    fontWeight: '500',
    marginLeft: 6,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    padding: 20,
  },
  modalContent: {
    backgroundColor: Colors.cardDark,
    borderRadius: 16,
    padding: 20,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.allWhite,
  },
  input: {
    backgroundColor: Colors.backgroundDark,
    borderRadius: 8,
    padding: 12,
    color: Colors.allWhite,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: Colors.borderDark,
  },
  saveButton: {
    backgroundColor: Colors.primary,
    borderRadius: 8,
    padding: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  saveButtonText: {
    color: Colors.allWhite,
    fontSize: 16,
    fontWeight: 'bold',
  },
});
