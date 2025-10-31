SELECT T1.*
FROM (SELECT F.NUFIN,
             (SELECT NOME
              FROM TWFELE
              WHERE CODPRN = PRN.CODPRN AND VERSAO = PRN.VERSAO AND IDELEMENTO = TAR.IDELEMENTO) AS NOMETAREFA,
             T.TNF_IDINSTPRN                                                                     AS PROCESSINSTANCEID,
             PRN.VERSAO,
             TAR.IDINSTTAR                                                                       AS TASKINSTANCEID,
             TAR.DHCONCLUSAO,
             TAR.SITUACAOEXEC,
             TAR.IDELEMENTO                                                                      AS TASKIDELEMENTO,
             PRN.CODPRN                                                                          AS PROCESSID
      FROM TGFCAB_TNF T
               INNER JOIN TGFFIN F ON T.NUNOTA = F.NUNOTA
               INNER JOIN TWFITAR TAR ON TAR.IDINSTPRN = T.TNF_IDINSTPRN
               INNER JOIN TWFIPRN PRN ON PRN.IDINSTPRN = T.TNF_IDINSTPRN
      WHERE F.DHBAIXA IS NULL
        AND TAR.DHCONCLUSAO IS NULL
        AND TAR.SITUACAOEXEC <> 'F'
        AND TAR.IDELEMENTO = 'UserTask_13pfnay'
        AND F.PROVISAO = 'N') T1
WHERE T1.PROCESSID = 9
  AND T1.NUFIN = :NUFIN