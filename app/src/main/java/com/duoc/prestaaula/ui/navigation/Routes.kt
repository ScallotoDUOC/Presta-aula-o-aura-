package com.duoc.prestaaula.ui.navigation

object Routes {
    const val EQUIPMENT = "equipment"
    const val LOANS = "loans"
    const val REPORTS = "reports"
    const val PROFILE = "profile"
    const val ADMIN = "admin_dashboard"
    const val ADMIN_BLOCKS = "admin_blocks"
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_AUDIT = "admin_audit"
    const val ADMIN_USER_DETAIL = "admin_user/{userId}"
    const val ADMIN_BLOCK_FORM = "admin_block_form/{equipmentId}"
    const val UTP = "utp_dashboard"
    const val UTP_OCCUPANCY = "utp_occupancy"
    const val UTP_INCIDENTS = "utp_incidents"

    const val EQUIPMENT_DETAIL = "equipment_detail/{id}"
    const val EQUIPMENT_FORM = "equipment_form/{id}"
    const val RESERVE = "reserve/{equipmentId}"
    const val LOAN_DETAIL = "loan/{id}"
    const val RETURN = "return/{loanId}"

    fun equipmentDetail(id: Long) = "equipment_detail/$id"
    fun equipmentForm(id: Long) = "equipment_form/$id"
    fun reserve(equipmentId: Long) = "reserve/$equipmentId"
    fun loanDetail(id: Long) = "loan/$id"
    fun returnLoan(loanId: Long) = "return/$loanId"
    fun adminBlockForm(equipmentId: Long) = "admin_block_form/$equipmentId"
    fun adminUser(userId: Long) = "admin_user/$userId"
}
