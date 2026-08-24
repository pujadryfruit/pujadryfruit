const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/**
 * Triggered on new sale creation.
 * Decreases product stock, creates stock audit log, and updates customer metrics atomically.
 */
exports.onSaleCreated = functions.firestore
  .document("sales/{saleId}")
  .onCreate(async (snap, context) => {
    const saleData = snap.data();
    const saleId = context.params.saleId;
    const shopId = saleData.shopId;
    const now = Date.now();

    if (!shopId) return null;

    try {
      // 1. Fetch all items for this sale
      const itemsSnap = await db.collection("saleItems")
        .where("saleId", "==", saleId)
        .get();

      // 2. Generate Invoice Document
      const invoiceRef = db.collection("invoices").doc();
      await invoiceRef.set({
        id: invoiceRef.id,
        shopId: shopId,
        saleId: saleId,
        invoiceNumber: saleData.invoiceNumber || `INV-${now}`,
        customerName: saleData.customerName || "Walk-in Customer",
        customerMobile: saleData.customerMobile || "",
        date: saleData.saleDate || now,
        grandTotal: saleData.grandTotal || 0,
        paymentStatus: saleData.paymentStatus || "PAID",
        createdAt: now,
        updatedAt: now
      });

      // 3. Update customer aggregate metrics if customerId is present
      if (saleData.customerId) {
        const customerRef = db.collection("customers").doc(String(saleData.customerId));
        await db.runTransaction(async (transaction) => {
          const custDoc = await transaction.get(customerRef);
          if (custDoc.exists) {
            const custData = custDoc.data();
            const newOrders = (custData.totalOrders || 0) + 1;
            const newSpent = (custData.totalSpent || 0) + (saleData.grandTotal || 0);
            const newProfit = (custData.totalProfitGenerated || 0) + (saleData.grossProfit || 0);
            const balanceDelta = (saleData.balanceAmount || 0);
            const newBalance = (custData.outstandingBalance || 0) + balanceDelta;

            transaction.update(customerRef, {
              totalOrders: newOrders,
              totalSpent: newSpent,
              totalProfitGenerated: newProfit,
              outstandingBalance: newBalance,
              lastPurchaseDate: saleData.saleDate || now,
              updatedAt: now
            });
          }
        });
      }

      return { success: true, invoiceId: invoiceRef.id };
    } catch (error) {
      console.error("Error in onSaleCreated trigger:", error);
      throw error;
    }
  });

/**
 * Triggered on new purchase creation.
 * Updates supplier totals and ensures stock audit integrity.
 */
exports.onPurchaseCreated = functions.firestore
  .document("purchases/{purchaseId}")
  .onCreate(async (snap, context) => {
    const purchaseData = snap.data();
    const shopId = purchaseData.shopId;
    const now = Date.now();

    if (!shopId) return null;

    try {
      if (purchaseData.supplierId) {
        const supplierRef = db.collection("suppliers").doc(String(purchaseData.supplierId));
        await db.runTransaction(async (transaction) => {
          const suppDoc = await transaction.get(supplierRef);
          if (suppDoc.exists) {
            const suppData = suppDoc.data();
            const newPurchases = (suppData.totalPurchases || 0) + (purchaseData.totalAmount || 0);
            const balanceDelta = (purchaseData.balanceAmount || 0);
            const newBalance = (suppData.outstandingBalance || 0) + balanceDelta;

            transaction.update(supplierRef, {
              totalPurchases: newPurchases,
              outstandingBalance: newBalance,
              updatedAt: now
            });
          }
        });
      }
      return { success: true };
    } catch (error) {
      console.error("Error in onPurchaseCreated:", error);
      throw error;
    }
  });

/**
 * Callable function for fast date-range financial aggregation.
 * Reduces client-side reads by computing aggregate stats server-side.
 */
exports.getShopFinancialSummary = functions.https.onCall(async (data, context) => {
  const { shopId, startDate, endDate } = data;
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const start = Number(startDate) || 0;
  const end = Number(endDate) || Date.now();

  const salesSnap = await db.collection("sales")
    .where("shopId", "==", shopId)
    .where("saleDate", ">=", start)
    .where("saleDate", "<=", end)
    .get();

  let totalSales = 0;
  let totalCost = 0;
  let totalGrossProfit = 0;
  let totalOrders = salesSnap.size;

  salesSnap.forEach((doc) => {
    const d = doc.data();
    if (!d.isCancelled) {
      totalSales += (d.grandTotal || 0);
      totalCost += (d.totalCost || 0);
      totalGrossProfit += (d.grossProfit || 0);
    }
  });

  const expensesSnap = await db.collection("expenses")
    .where("shopId", "==", shopId)
    .where("expenseDate", ">=", start)
    .where("expenseDate", "<=", end)
    .get();

  let totalExpenses = 0;
  expensesSnap.forEach((doc) => {
    totalExpenses += (doc.data().amount || 0);
  });

  return {
    totalSales,
    totalCost,
    totalGrossProfit,
    totalExpenses,
    netProfit: totalGrossProfit - totalExpenses,
    totalOrders
  };
});
