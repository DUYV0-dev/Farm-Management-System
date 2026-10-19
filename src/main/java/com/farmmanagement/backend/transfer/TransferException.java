package com.farmmanagement.backend.transfer;

import java.util.List;

public class TransferException extends RuntimeException {
    public record Issue(int row, String field, String code, String message) {}
    private final List<Issue> issues;
    public TransferException(List<Issue> issues) {
        super("Tệp dữ liệu chưa hợp lệ. Không có bản ghi nào được lưu.");
        this.issues=List.copyOf(issues);
    }
    public List<Issue> issues() { return issues; }
    public static TransferException file(String code,String message) {
        return new TransferException(List.of(new Issue(0,"file",code,message)));
    }
}
