// Port of Categories.kt. `icon` is a name in icon-names.txt.
export interface Cat { id: string; label: string; icon: string }
export const CATS: Cat[] = [
  { id: "", label: "General", icon: "receipt_long" },
  { id: "food", label: "Food & drink", icon: "restaurant" },
  { id: "groceries", label: "Groceries", icon: "shopping_cart" },
  { id: "transport", label: "Transport", icon: "directions_car" },
  { id: "fuel", label: "Fuel", icon: "local_gas_station" },
  { id: "stay", label: "Stay", icon: "hotel" },
  { id: "rent", label: "Rent & home", icon: "home" },
  { id: "utilities", label: "Bills", icon: "bolt" },
  { id: "fun", label: "Entertainment", icon: "movie" },
  { id: "shopping", label: "Shopping", icon: "shopping_bag" },
  { id: "travel", label: "Travel", icon: "flight" },
  { id: "health", label: "Health", icon: "local_hospital" },
  { id: "gifts", label: "Gifts", icon: "redeem" },
];
export const catOf = (id?: string | null): Cat => CATS.find((c) => c.id === (id ?? "")) ?? CATS[0];

const KEYWORDS: Record<string, string[]> = {
  food: ["dinner", "lunch", "breakfast", "brunch", "snack", "restaurant", "cafe", "coffee", "tea", "chai", "pizza", "burger", "biryani", "thali", "dosa", "food", "swiggy", "zomato", "eat", "meal", "drinks", "beer", "bar", "pub", "wine", "dessert", "ice cream", "bakery", "shack", "dhaba", "canteen", "mess", "party"],
  groceries: ["grocer", "vegetable", "veggies", "fruit", "milk", "bigbasket", "blinkit", "zepto", "dmart", "supermarket", "kirana", "instamart", "provisions", "mart"],
  transport: ["taxi", "cab", "uber", "ola", "rapido", "auto", "rickshaw", "metro", "bus", "train", "ticket", "scooter", "bike rental", "rental", "toll", "parking", "ride", "airport"],
  fuel: ["petrol", "diesel", "fuel", "gas", "cng", "charging"],
  stay: ["hotel", "villa", "airbnb", "stay", "hostel", "resort", "homestay", "room", "booking", "lodge", "camp"],
  rent: ["rent", "maintenance", "furniture", "cleaning", "maid", "cook", "society", "deposit", "broker", "repair", "plumber"],
  utilities: ["wifi", "wi-fi", "internet", "broadband", "electric", "water bill", "bill", "recharge", "phone", "mobile", "gas cylinder", "lpg", "netflix", "prime", "hotstar", "spotify", "subscription", "dth"],
  fun: ["movie", "cinema", "pvr", "concert", "show", "game", "bowling", "club", "netflix", "trek", "trekking", "rafting", "adventure", "park", "museum", "sports", "match", "turf"],
  shopping: ["shopping", "amazon", "flipkart", "myntra", "clothes", "shirt", "shoes", "mall", "gadget", "store"],
  travel: ["flight", "flights", "airline", "indigo", "visa", "passport", "trip", "tour", "travel", "cruise", "luggage"],
  health: ["doctor", "medicine", "pharmacy", "hospital", "clinic", "medical", "gym", "dentist", "lab test", "apollo", "medplus"],
  gifts: ["gift", "present", "birthday", "wedding", "flowers", "cake", "anniversary"],
};
/** Guesses a category from what the expense was for ("Uber to airport" gives Transport). Null when nothing matches. */
export function guessCategory(text: string): Cat | null {
  const words = text.toLowerCase().split(/[^a-z0-9-]+/).filter(Boolean);
  if (!words.length) return null;
  const flat = " " + words.join(" ") + " ";
  let best: Cat | null = null, bestScore = 0;
  for (const [id, list] of Object.entries(KEYWORDS)) {
    const hits = list.filter((k) => (k.length <= 3 ? flat.includes(` ${k} `) : flat.includes(` ${k}`))).length;
    if (hits > bestScore) { best = catOf(id); bestScore = hits; }
  }
  return best;
}
