package app.hissa

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/** Expense categories. [id] is what is stored; null / unknown shows as General. */
enum class Cat(val id: String, val label: String, val icon: ImageVector) {
    GENERAL("", "General", Icons.AutoMirrored.Rounded.ReceiptLong),
    FOOD("food", "Food & drink", Icons.Rounded.Restaurant),
    GROCERIES("groceries", "Groceries", Icons.Rounded.ShoppingCart),
    TRANSPORT("transport", "Transport", Icons.Rounded.DirectionsCar),
    FUEL("fuel", "Fuel", Icons.Rounded.LocalGasStation),
    STAY("stay", "Stay", Icons.Rounded.Hotel),
    RENT("rent", "Rent & home", Icons.Rounded.Home),
    UTILITIES("utilities", "Bills", Icons.Rounded.Bolt),
    FUN("fun", "Entertainment", Icons.Rounded.Movie),
    SHOPPING("shopping", "Shopping", Icons.Rounded.ShoppingBag),
    TRAVEL("travel", "Travel", Icons.Rounded.Flight),
    HEALTH("health", "Health", Icons.Rounded.LocalHospital),
    GIFTS("gifts", "Gifts", Icons.Rounded.CardGiftcard);

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == (id ?: "") } ?: GENERAL }
}

private val keywords: Map<Cat, List<String>> = mapOf(
    Cat.FOOD to listOf("dinner", "lunch", "breakfast", "brunch", "snack", "restaurant", "cafe", "coffee", "tea", "chai", "pizza", "burger", "biryani", "thali", "dosa", "food", "swiggy", "zomato", "eat", "meal", "drinks", "beer", "bar", "pub", "wine", "dessert", "ice cream", "bakery", "shack", "dhaba", "canteen", "mess", "party"),
    Cat.GROCERIES to listOf("grocer", "vegetable", "veggies", "fruit", "milk", "bigbasket", "blinkit", "zepto", "dmart", "supermarket", "kirana", "instamart", "provisions", "mart"),
    Cat.TRANSPORT to listOf("taxi", "cab", "uber", "ola", "rapido", "auto", "rickshaw", "metro", "bus", "train", "ticket", "scooter", "bike rental", "rental", "toll", "parking", "ride", "airport"),
    Cat.FUEL to listOf("petrol", "diesel", "fuel", "gas", "cng", "charging"),
    Cat.STAY to listOf("hotel", "villa", "airbnb", "stay", "hostel", "resort", "homestay", "room", "booking", "lodge", "camp"),
    Cat.RENT to listOf("rent", "maintenance", "furniture", "cleaning", "maid", "cook", "society", "deposit", "broker", "repair", "plumber"),
    Cat.UTILITIES to listOf("wifi", "wi-fi", "internet", "broadband", "electric", "water bill", "bill", "recharge", "phone", "mobile", "gas cylinder", "lpg", "netflix", "prime", "hotstar", "spotify", "subscription", "dth"),
    Cat.FUN to listOf("movie", "cinema", "pvr", "concert", "show", "game", "bowling", "club", "netflix", "trek", "trekking", "rafting", "adventure", "park", "museum", "sports", "match", "turf"),
    Cat.SHOPPING to listOf("shopping", "amazon", "flipkart", "myntra", "clothes", "shirt", "shoes", "mall", "gadget", "store"),
    Cat.TRAVEL to listOf("flight", "flights", "airline", "indigo", "visa", "passport", "trip", "tour", "travel", "cruise", "luggage"),
    Cat.HEALTH to listOf("doctor", "medicine", "pharmacy", "hospital", "clinic", "medical", "gym", "dentist", "lab test", "apollo", "medplus"),
    Cat.GIFTS to listOf("gift", "present", "birthday", "wedding", "flowers", "cake", "anniversary"),
)

/** Guesses a category from what the expense was for: "Uber to airport" gives Transport. Null when nothing matches. */
fun guessCategory(text: String): Cat? {
    val words = text.lowercase().split(Regex("[^a-z0-9-]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    val flat = " " + words.joinToString(" ") + " "
    var best: Cat? = null
    var bestScore = 0
    for ((cat, list) in keywords) {
        // whole word, or a word that starts with the keyword ("groceries" for "grocer"); keywords of 3 letters or fewer must match whole.
        val hits = list.count { k -> if (k.length <= 3) flat.contains(" $k ") else flat.contains(" $k") }
        if (hits > bestScore) { best = cat; bestScore = hits }
    }
    return best
}
